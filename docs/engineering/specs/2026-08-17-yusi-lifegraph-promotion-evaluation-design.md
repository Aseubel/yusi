# LifeGraph Promotion 固定抽取与真实 H2 回放设计

## 状态

- 状态：待用户评审确认
- 日期：2026-08-17
- 所属阶段：Phase 4 第三刀
- 相关路线图：[Yusi Agent 产品与工程演进计划](../plans/2026-08-04-yusi-agent-product-roadmap.md)
- 相关评测契约：[Yusi Agent 产品事件与评测基线](../specs/2026-08-04-yusi-agent-product-event-contract.md)
- 前置回放：[LifeGraph / Timeline 离线回放基线](../../superpowers/specs/2026-08-16-offline-evaluation-replay-baseline-design.md)
- 前置回放：[中期记忆生命周期与消费边界回放](../../superpowers/specs/2026-08-16-memory-lifecycle-evaluation-design.md)

## 1. 目标

本切片建立独立的 `lifegraph-promotion-v1` 离线评测套件，固定
`LifeGraphExtractionResult` 和 `confirmedImportantPersonKeys` 两个 promotion 输入，验证
`LifeGraphPromotionPolicy` 的确定性输出，并把同一份抽取结果送入真实 H2 的
`LifeGraphBuildServiceImpl`，核对长期实体、关系和来源证据的实际落库结果。

本切片完成后，以下规则有独立的机器可读回放证据：

1. 只有有用户直接生活语义的 Person 才能成为重要人物入口。
2. 已确认的重要人物只能向一跳属性或事件扩展，不能自动引入另一个 Person。
3. 自动关系必须有非空证据片段，且关系置信度必须达到 `0.6`；等于 `0.6` 通过，低于
   `0.6` 拒绝。
4. `MENTIONED`、`MENTIONED_IN`、`SAID`、`RELATED_TO` 等
   `REJECTED_RELATIONS` 不进入自动长期关系表。
5. policy 接受的实体、关系、实体来源证据、关系来源证据和 Diary mention 与 H2 中的
   实际结果一致。

## 2. 勘察结论

### 2.1 `LifeGraphPromotionPolicy`

当前 policy 的入口是：

```java
PromotionResult promote(LifeGraphExtractionResult extraction,
                        Set<String> confirmedImportantPersonKeys)
```

当前实现已经提供本切片需要固化的基础行为：

- `MIN_RELATION_CONFIDENCE` 为 `0.6`；
- 支持的实体类型直接来自 `LifeGraphEntity.EntityType`；
- Person 关系和属性/事件关系由 `LifeGraphRelationType` 分组；
- `MENTIONED`、`MENTIONED_IN`、`SAID`、`RELATED_TO` 被标记为禁止自动图谱关系；
- 第一轮只处理 `User <-> Person` 的直接关系，并把 Person 加入重要人物集合；
- 第二轮只允许已确认 User/Person 节点连接到非 User 的属性或事件实体；
- Person 到另一个 Person 的关系不会进入第二轮，因此不会发生人物关系扩散；
- 结果中的实体集合来自被接受关系的端点，不会仅因抽取结果中出现实体就落库；
- 同一规范化方向关系在 `relations` 中只保留一条，出现次数进入
  `relationOccurrences`。

因此本切片不重新定义 promotion 规则，也不把 `importance` 加入通过条件；只把当前规则
变成可回放的输入、期望和 H2 结果断言。

### 2.2 `LifeGraphBuildServiceImpl`

当前 Diary 写入链路为：

```text
upsertFromDiary
  -> LifeGraphExtractor.extract
  -> JSON 解析为 LifeGraphExtractionResult
  -> 从已有 H2 关系推导 confirmedImportantPersonKeys
  -> LifeGraphPromotionPolicy.promote
  -> 事务内删除当前来源旧贡献
  -> 写入通过 promotion 的实体、别名、实体证据、Diary mention
  -> 写入关系、关系证据并刷新聚合字段
```

测试可以将 `LifeGraphExtractor` 替换为 Mockito fixture boundary。这样不会调用远程 LLM，
但仍然经过生产 `LifeGraphBuildServiceImpl`、JPA repository、实体/关系证据表和
`LifeGraphMention` 表。对于 `confirmedImportantPersonKeys`，回放在 H2 中预置一条
`User -> Person` 的人工确认关系，生产代码会通过 `findConfirmedImportantPersons` 得到与
fixture 相同的 key；这比直接调用私有写入方法更接近真实消费路径。

本切片不覆盖 `LifeGraphTaskBatchService` 的 revision 排序、来源替换/撤销和 Timeline；
这些行为已经属于 `lifegraph-timeline-v1`。第三刀只使用独立虚构用户和独立 Diary source，
避免套件之间互相污染。

### 2.3 既有 evaluation 套件

- `LifeGraphTimelineEvaluationTest` 已证明固定 extractor + 真实 H2 + production service 的
  回放方式可用，但它的重点是 revision、重复贡献、删除残留和 Timeline 资格；它没有把
  policy 入参、置信度边界和禁止关系作为独立契约输出。
- `MemoryLifecycleEvaluationTest` 已使用共享
  `OfflineEvaluationReportWriter`，报告路径为 `target/evaluation/*.json`，并由默认 Maven
  测试接收。
- `EvaluationFixtureRedLineValidator` 已拒绝原文、Prompt、工具参数/结果、密钥等字段，新的
  loader 在此之上补充 LifeGraph 类型、ID、关系和证据 token 的严格校验。
- CI 已归档 `target/evaluation/*.json`，本切片不需要修改 CI。

## 3. 方案比较与选择

### 方案 A：测试内直接把 `PromotionResult` 写入 H2

测试直接调用 policy，然后复制 `LifeGraphBuildServiceImpl` 的实体、关系和证据写入逻辑。
实现改动最少，但会在测试中重新实现生产聚合逻辑，容易出现“测试通过而生产写入路径未覆盖”。

### 方案 B：固定 extractor + 直接 policy 断言 + 真实 BuildService/H2 回放（采用）

先以 fixture typed object 直接调用 policy，核对接受集合和 occurrence；再把同一对象序列化
为 fixture JSON，配置 `LifeGraphExtractor` mock，调用真实 `upsertFromDiary`，最后从 H2
repository 核对实体、关系和证据行。

该方案同时满足两个边界：policy 的 typed 入参明确可见，生产写入链路仍由真实
`LifeGraphBuildServiceImpl` 执行；测试不新增生产 API，不调用 LLM，不复制生产写入算法。代价
是需要为 confirmed person 在 H2 中预置人工确认关系，并在 evaluator 中区分预置关系和当前
fixture source 的自动贡献。

### 方案 C：新增生产 typed extraction 写入 API

为 BuildService 增加接收 `LifeGraphExtractionResult` 的公开方法，评测直接调用该方法。它能
更直接地传递 confirmed keys，但会扩大生产接口、增加绕过正常抽取入口的调用面；本切片没有
真实产品消费者，不值得为评测专门增加生产契约。

本设计采用方案 B。

## 4. 评测契约

### 4.1 套件与文件

| 项目 | 固定值 |
| --- | --- |
| suite | `lifegraph-promotion-v1` |
| fixture | `src/test/resources/evaluation/lifegraph-promotion-v1-fixtures.json` |
| report | `target/evaluation/lifegraph-promotion-v1-report.json` |
| JUnit 入口 | `LifeGraphPromotionEvaluationTest` |
| case | `EVAL-MEM-003` |
| scenarios | `EVAL-MEM-003-A`、`EVAL-MEM-003-B`、`EVAL-MEM-003-C` |
| inputVersion | `fixture-v1` |
| expectedVersion | `expectation-v1` |

`EVAL-MEM-003` 沿用现有 LifeGraph 评测使用的 `MEM` 域编号，不新建第二套产品样例编号。
`scenarioId` 只用于区分同一 case 下的独立边界。

### 4.2 场景 A：直接重要人物和一跳属性/事件

虚构抽取结果包含 User、一个 Person、一个 Item、一个 Event 和一个未被允许扩展的 Person。
关系至少包含：

- `User -> Person` 的 `PARTNER_OF`，confidence 恰好为 `0.60`，有证据 token；
- `Person -> Item` 的 `LIKES`，有证据 token；
- `Person -> Event` 的 `PARTICIPATED_IN`，有证据 token；
- `Person -> Person` 的 `FRIEND_OF`，有证据 token；
- `MENTIONED`、`SAID`、`RELATED_TO` 各至少一条；
- 一条缺少 `evidenceSnippet` 的长期关系；
- 一条 confidence 为 `0.59` 的长期关系。

期望：

- User、直接 Person、Item、Event 被 policy/BuildService 结果保留；
- 直接 Person 成为重要人物，Item/Event 只通过该人物的一跳关系进入；
- Person->Person、所有 `REJECTED_RELATIONS`、缺证据关系和低置信度关系不落库；
- 当前来源为每个被接受的非 User 实体产生一条实体证据、为每条被接受关系产生一条关系
  证据；Diary source 同时产生对应 mention；
- `importance` 值即使存在，也只作为写入字段，不进入本场景断言或排序。

### 4.3 场景 B：confirmed important person 的一跳消费

H2 预置一个 User、一个 Person 和一条 `origin=MANUAL` 的直接 User->Person 关系。fixture
的 `confirmedImportantPersonKeys` 只包含该 Person；当前固定抽取结果不再提供 User->Person
关系，只提供：

- 已确认 Person -> Item 的 `LIKES`；
- 已确认 Person -> Event 的 `PARTICIPATED_IN`；
- 已确认 Person -> 另一个 Person 的 `FRIEND_OF`。

期望：

- 两条属性/事件关系和其端点被当前来源接受；
- 已确认 Person 的确认入口来自 H2 预置关系，当前来源仍有自己的实体证据；
- 新的 Person 不因人物关系扩散进入长期图谱；
- 预置人工关系保留，当前来源只新增自动关系证据，不把人工关系伪装成当前来源贡献。

### 4.4 场景 C：证据、置信度和禁止关系全部拒绝

虚构抽取结果只提供不能通过 promotion 的关系：User->Person 缺证据、User->Person 低于
阈值、未确认 Person->Item 的属性关系、Person->Person 的人物关系以及
`REJECTED_RELATIONS`。

期望除自动创建的 User 实体外，不产生当前来源实体、关系、实体证据、关系证据或 Diary
mention。该场景证明“没有有效关系时，抽取结果中的实体不会因为单独出现而长期化”。

## 5. 数据流与 H2 边界

```text
脱敏 fixture JSON
  -> EvaluationFixtureRedLineValidator
  -> LifeGraphPromotionFixtureLoader 严格 typed 校验
  -> LifeGraphPromotionPolicy.promote(extraction, confirmedKeys)
  -> policy 结果断言
  -> H2 预置 confirmed person（仅场景 B）
  -> Mockito LifeGraphExtractor 返回同一 extraction JSON
  -> LifeGraphBuildService.upsertFromDiary
  -> 真实 JPA/H2 entity/relation/evidence/mention
  -> 低敏计数断言
  -> OfflineEvaluationReportWriter
  -> target/evaluation/lifegraph-promotion-v1-report.json
```

H2 使用既有 `application-test.yml`：内存数据库、`MODE=MySQL`、`ddl-auto=create-drop`，并通过
`TestInfrastructureConfig` 和 `@MockBean` 隔离 Milvus、Redis、OSS、PromptManager 和
LifeGraphExtractor。回放不启动 Web、gRPC、调度线程、Milvus、Redis 或模型服务。

每个 scenario 使用独立的 `fixture-user-promotion-*` 和
`fixture-diary-promotion-*`。不依赖事务回滚来清理跨 scenario 数据，也不写开发库、生产库
或任何评测专用 migration。

## 6. Fixture 脱敏规则

新 loader 先调用共享 `EvaluationFixtureRedLineValidator.validateTree`，再执行 typed 校验：

- 用户 ID 使用 `fixture-user-*`；source ID 使用 `fixture-diary-promotion-*`；实体规范名和
  展示名使用 `fixture-*` 或 User sentinel `__USER__`；
- `evidenceSnippet` 和 mention `snippet` 必须匹配 `evidence-token-[a-z0-9-]+`；
- `props` 只允许为空对象或缺省；不放原始偏移、正文或自然语言说明；
- 实体类型、关系类型、confidence 范围、关系端点和 confirmed person key 必须可解析；
- fixture 不允许 `plainContent`、`rawText`、`prompt`、`toolArguments`、`toolResult`、
  `secret`、`password`、`content` 等字段；
- 报告不复制实体 key、displayName、summary、evidence token、Prompt、工具参数/结果或异常
  message，只保留计数、版本槽位和固定 violation code。

fixture 中的合成 token 只存在于测试输入和 H2 测试数据中，不能进入报告、日志或普通产品
事件。`generatedAt` 是报告运行元数据，不参与结果相等比较。

## 7. 报告契约

直接复用 `OfflineEvaluationReportWriter` 的 schema version `1`、runner version `v1` 和四类
版本槽位：`model`、`prompt`、`retrieval`、`ranking`。本套件使用 fixture baseline：

```json
{
  "model": {"provider": "fixture", "name": "none", "version": "fixture-v1"},
  "prompt": {"key": "fixture", "version": "fixture-v1", "locale": "zh-CN"},
  "retrieval": {"strategy": "not_applicable", "version": "fixture-v1"},
  "ranking": {"strategy": "not_applicable", "version": "fixture-v1"}
}
```

每个 case 的 `actualSummary` 只允许数值计数，例如：

- `entityCount`、`userEntityCount`、`personEntityCount`、`autoEntityCount`；
- `relationCount`、`autoRelationCount`、`sourceRelationEvidenceCount`；
- `entityEvidenceCount`、`relationEvidenceCount`、`mentionCount`。

失败只写固定 code，例如 `FIXTURE_INVALID`、`REPLAY_EXECUTION`、`POLICY_BOUNDARY`、
`H2_BOUNDARY`、`EVIDENCE_BOUNDARY`、`REPORT_LOW_SENSITIVITY`；不写异常文本。任一 scenario
失败或报告状态不是 `PASS` 都使 JUnit 失败，默认 `mvn test` 因此阻断回归。

## 8. 非目标

- 不改 `importance` 字段、人物字段或任何 importance 决策消费方；
- 不改 LifeGraph 生产规则、关系白名单、来源撤销、revision 幂等或 Timeline；
- 不新增生产 API、数据库表、migration、评测 API、用户页面或运行时评测任务；
- 不调用 LLM、Prompt、Embedding、Milvus、Redis、OSS 或真实用户数据；
- 不评测自然语言回答、抽取模型质量、检索质量、匹配排序或主动问候；
- 不修改 CI，因为现有 `target/evaluation/*.json` artifact 已覆盖新报告。

## 9. 验收标准

1. `LifeGraphPromotionPolicyTest` 明确覆盖 `0.60` 通过、低于阈值拒绝、缺证据拒绝、
   confirmed person 一跳属性和 REJECTED_RELATIONS/Person->Person 拒绝。
2. 三个 `EVAL-MEM-003-*` scenario 使用同一份 typed extraction 做 policy 断言和真实 H2
   BuildService 回放。
3. H2 断言同时覆盖实体、关系、实体证据、关系证据和 Diary mention，且人工 confirmed
   关系与当前来源自动贡献分开统计。
4. fixture 通过共享脱敏校验和 LifeGraph 专属 typed 校验；报告不含合成证据 token 或任何
   用户正文类字段。
5. focused evaluation test 和全量 `.\mvnw.cmd -q test` 通过，报告写入
   `target/evaluation/lifegraph-promotion-v1-report.json` 并可由 CI artifact 归档。
6. 实现和验证期间不启动服务、不连接远程依赖，不触碰 Phase 4 第四项 importance 消费
   或扩展 Backlog 条目。
