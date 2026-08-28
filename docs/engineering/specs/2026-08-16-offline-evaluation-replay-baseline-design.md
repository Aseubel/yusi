# 离线评测回放基线设计

## 状态

- 设计范围：LifeGraph promotion、来源替换/撤销、Timeline 候选和机器可读基线报告
- 状态：已确认，实施中
- 日期：2026-08-16
- 相关契约：[Yusi Agent 产品事件与评测基线](../../engineering/specs/2026-08-04-yusi-agent-product-event-contract.md)
- 相关路线图：[Yusi Agent 产品与工程演进计划](../../engineering/plans/2026-08-04-yusi-agent-product-roadmap.md)

## 目标

在不启动应用服务、不访问远程模型、不连接真实用户数据的前提下，建立第一批可重复的
LifeGraph / Timeline 结构化回放。回放使用脱敏 fixture 驱动真实的 LifeGraph promotion、
JPA 来源证据和 Timeline 聚合逻辑，输出稳定的机器可读报告，并作为 CI 的默认回归门槛。

本切片是 Phase 4 的第一刀。它先验证服务端确定性规则和来源生命周期，不把远程 LLM、Prompt
质量、对话语气、匹配排序或主动问候混入同一个执行器。后续模型回放可以复用本切片的 fixture
编号、报告 schema 和版本字段。

## 已确认的边界

### 1. 回放持久化方案：H2 内嵌测试库

回放执行器使用 `src/test/resources/application-test.yml` 已配置的 H2 内存数据库，采用
`MODE=MySQL`、`ddl-auto=create-drop` 和测试 profile。每个评测 case 使用独立的虚构用户范围，
case 结束后回滚事务或清理该用户的所有派生行；不写入开发库、生产库或新的业务 migration。

选择 H2 而不是内存 fake 的原因：

- 真实验证 `life_graph_entity_evidence`、`life_graph_relation_evidence`、`life_graph_mention`
  的唯一约束和来源 revision 字段；
- 真实验证来源替换事务删除旧证据、重新聚合权重和清理孤儿实体/关系的行为；
- 真实验证 `LifeTimelineService` 的 repository 查询、可见性和日期过滤；
- 不需要为了评测复制一套内存聚合逻辑，避免测试通过但生产 JPA 行为不一致。

测试使用 fixture extractor 和固定 Prompt snapshot mock，不执行真实模型调用。H2 只是测试隔离
持久化介质，不代表生产会新增评测数据库或运行时评测表。

### 2. 基线报告 schema 与版本槽位

每次回放写出 `target/evaluation/lifegraph-timeline-v1-report.json`。报告 schema 固定为版本 1，
即使本切片不调用模型，也必须保留后续对比需要的四类版本信息：

```json
{
  "schemaVersion": 1,
  "suiteId": "lifegraph-timeline-v1",
  "runnerVersion": "v1",
  "generatedAt": "2026-08-16T00:00:00Z",
  "cases": [
    {
      "caseId": "EVAL-MEM-002",
      "scenarioId": "EVAL-MEM-002-A",
      "status": "PASS",
      "inputVersion": "fixture-v1",
      "expectedVersion": "expectation-v1",
      "versions": {
        "model": { "provider": "fixture", "name": "none", "version": "fixture-v1" },
        "prompt": { "key": "fixture", "version": "fixture-v1", "locale": "zh-CN" },
        "retrieval": { "strategy": "not_applicable", "version": "fixture-v1" },
        "ranking": { "strategy": "not_applicable", "version": "fixture-v1" }
      },
      "assertionCount": 4,
      "passedAssertionCount": 4,
      "violationCodes": [],
      "actualSummary": {
        "entityCount": 1,
        "relationCount": 1,
        "entityEvidenceCount": 1,
        "relationEvidenceCount": 1,
        "timelineNodeCount": 0
      }
    }
  ],
  "summary": {
    "caseCount": 1,
    "passedCaseCount": 1,
    "failedCaseCount": 0,
    "assertionCount": 4,
    "passedAssertionCount": 4,
    "status": "PASS"
  }
}
```

`generatedAt` 只属于运行元数据，不参与结果相等比较。case 顺序、断言顺序和 violation code
必须稳定；异常只转换为固定错误类别，不把 exception message、原文、Prompt、工具参数或查询
结果写入报告。`actualSummary` 只包含计数、状态和 fixture 中的合成 key，不包含真实文本。

后续模型回放只需替换 `model`、`prompt`、`retrieval`、`ranking` 四个槽位和执行 adapter，
不改变 case ID、报告顶层结构或 CI 归档路径。

### 3. 与既有 `EVAL-*` 编号体系的关系

回放 fixture 继续使用产品契约的 `EVAL-<DOMAIN>-<NNN>` 编号，不创建第二套测试 ID，已有
`EVAL-CHAT-001` 至 `EVAL-ROOM-002` 不重命名、不复用和不改变语义。

本切片新增：

- `EVAL-MEM-002`：LifeGraph 来源替换、revision 幂等和来源撤销；
- `EVAL-TIMELINE-001`：Event 资格、时间证据和来源删除后的 Timeline 清理。

来源边界拆成 `scenarioId`，以便同一产品样例下表达多个必须同时满足的状态：

- `EVAL-MEM-002-A`：乱序 revision；
- `EVAL-MEM-002-B`：同 revision 重复事件；
- `EVAL-MEM-002-C`：删除后残留清零。

fixture registry 以 `caseId` 作为父级索引、以 `scenarioId` 作为可执行实例索引；报告同时保留
两者，后续可按产品领域聚合通过率。

### 4. 来源撤销必须覆盖的三类边界

#### A. 乱序 revision

对同一 `userId + sourceType + sourceId` 先应用 revision `3`，再投递 revision `1`。revision `1`
必须被识别为过期输入，不得撤销或覆盖 revision `3` 的实体证据、关系证据、mention、聚合权重
或 Timeline 候选。实现同时覆盖 `1 -> 3 -> 2` 的顺序，最终有效来源必须是最高已接受 revision。

#### B. 同 revision 重复事件

同一来源、同一 revision 和相同 fixture fingerprint 重复投递两次，只允许一份来源证据和一次
有效 occurrence 贡献；实体 `mentionCount`、关系 `weight`、Timeline 节点和证据行不能重复累加。
同 revision 但 fingerprint 不同属于冲突输入，必须返回固定 `DUPLICATE_REVISION_CONFLICT`，保留
已接受内容，不执行先删后写。

#### C. 删除后残留清零

应用来源后执行删除，必须验证以下结果：

- 该来源的实体证据、关系证据和 Diary mention 全部为零；
- 只由该来源贡献的自动实体、别名、自动关系和 Timeline 节点被清理；
- 仍由其他来源贡献的实体、关系、计数和 Timeline 节点继续保留；
- 人工实体和人工关系的人工基线不被删除，自动权重归零后保留人工基线；
- 不存在跨用户同 source ID 被误删的行。

### 5. fixture 脱敏红线

fixture 继承产品评测契约的红线：只允许虚构用户、虚构来源 ID、抽象主题、合成实体 key、
结构化期望和固定证据 token。禁止真实日记、真实 Plaza 内容、真实用户 ID、原始 Prompt、工具
参数、工具结果、模型完整输出、密钥、密码和可还原个人信息。

fixture schema 不提供 `plainContent`、`rawText`、`prompt`、`toolArguments`、`toolResult`、
`secret` 或 `password` 字段；证据只使用形如 `evidence-token-001` 的合成 token。加载器在执行前
校验 ID 前缀、禁止字段和字符串长度，违反红线时以 `FIXTURE_INVALID` 失败，不把违规值带入报告。

### 6. 机器可读结果与 CI 接线

回放执行器由 `LifeGraphTimelineEvaluationTest` 作为 JUnit 入口，使用真实 H2 repository、
fixture extractor 和确定性 assertion evaluator。每个 case 先写入隔离数据，再执行 promotion、
来源替换/删除和 Timeline 查询，收集 `EvaluationCaseResult`；全部 case 完成后写 JSON 报告，
并用 `summary.status == PASS` 和每个 case 的断言结果让 JUnit 测试失败或通过。

本切片选择接入 Maven 默认 test 套件，不设置独立 profile：fixture 小、无网络、无外部服务，
每次 `mvn -q test` 都必须经过该质量门槛。CI 额外归档 `target/evaluation/*.json` 作为构建产物；
开发者可以用以下命令只运行这一刀：

```powershell
.\mvnw.cmd -q "-Dtest=LifeGraphTimelineEvaluationTest" test
```

不允许通过 profile 默认跳过评测。若未来引入需要远程模型的长回放，另建独立 profile 和执行器，
不得降低本地确定性套件的默认门槛。

## 执行流程

```text
fixture JSON
  -> strict red-line validation
  -> H2 isolated case state
  -> fixture extractor / fixed prompt snapshot
  -> LifeGraphBuildService + LifeGraphPromotionPolicy
  -> source revision / duplicate / delete assertions
  -> LifeTimelineService query assertions
  -> EvaluationCaseResult
  -> target/evaluation/lifegraph-timeline-v1-report.json
  -> JUnit PASS/FAIL + CI artifact
```

任何 fixture 解析失败、来源状态不一致、残留行未清零或 Timeline 结果不符合期望都必须产生稳定
violation code 并使测试失败。执行器不修复生产数据、不调用生产 API、不启动 Web、gRPC、Redis、
Milvus 或模型服务。

## 生产事件边界与回放落点

回放不把 `LifeGraphBuildService` 的直接调用误认为完整的事件排序器。生产写入的接受边界仍由
来源任务/事件层负责：Diary 使用 `LifeGraphTask.sourceRevision` 与 `TaskExecution` 的来源版本
幂等键，Plaza 使用 `PlazaLifeGraphListener` 的来源版本幂等键；相同 revision 的重复事件复用
同一执行记录，旧 revision 在读取当前来源后短路。DELETE 事件也必须在执行前按当前来源 revision
判断，不能只保护 UPSERT。

评测执行器使用真实 H2 repository 直接验证成功替换、证据聚合和撤销结果，并在需要验证事件顺序
的场景通过真实 `LifeGraphTaskBatchService.processSingleTask` 进入生产的 revision 短路点。这样既
不会让测试依赖远程异步线程，也不会把任务层的顺序保证伪装成 BuildService 内部的全局事件账本。
本切片不新增评测专用生产表；若未来需要接收没有来源任务/事件包装的通用写入，必须另行设计持久化
来源状态与 fingerprint，而不能在本回放测试中临时引入内存状态。

## 评测断言范围

第一版只断言确定性结果：

- LifeGraph promotion 只接受有用户/重要人物生活语义和证据的实体关系；
- `Person -> Person` 自动扩散被拒绝；
- 来源 revision、重复事件和删除撤销遵守上述三类边界；
- 实体/关系证据、mention 和聚合计数与有效来源一致；
- Event 具备时间证据时才进入 Timeline；普通 Person、Topic 和无日期 Event 不进入；
- 来源删除后 Timeline 不保留孤儿节点；
- 全部结果保持用户隔离和低敏报告格式。

本切片不对自然语言回答质量、模型幻觉、Prompt 优劣、检索召回率、排序质量、匹配接受率或主动
触发合理性作结论；这些领域继续沿用既有 `EVAL-*` 契约，后续复用本报告 schema 扩展执行器。

## 非目标

- 不新增生产评测表、评测 API 或用户页面；
- 不把真实数据导入 H2 或提交真实样例；
- 不把 fixture 内容、异常正文或生产实体摘要写入报告；
- 不实现远程模型调用、Prompt 版本比较、GraphRAG 多跳质量评测或评分学习；
- 不改变 LifeGraph、来源证据和 Timeline 的生产业务规则，只为它们增加可回放验证。

## 验收标准

1. 设计和实现明确使用 H2 内嵌测试库，禁止中途切换为未声明的 fake 或真实数据库。
2. 报告包含 `model`、`prompt`、`retrieval`、`ranking` 四类版本槽位，且 schema version 固定。
3. 新样例沿用既有 `EVAL-*` 编号，父级 `caseId` 与 `scenarioId` 可追踪。
4. 乱序 revision、同 revision 重复事件和删除残留清零均有独立断言。
5. fixture 通过红线校验，不包含真实正文、秘密或可还原个人信息。
6. JSON 报告可被 CI 归档，JUnit 默认 `mvn test` 失败即阻断回归。
7. LifeGraph 和 Timeline 的确定性回放通过，且不启动服务、不访问远程依赖。
