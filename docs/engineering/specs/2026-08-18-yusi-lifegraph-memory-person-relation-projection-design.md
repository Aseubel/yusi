# LifeGraph 记忆中心人物关系投影设计

- 状态：评审通过，条件修正后实施
- 日期：2026-08-18
- 所属阶段：Phase 1 透明度补全，承接 Phase 4 决策消费切片
- 前置路线图：[Yusi Agent 产品与工程演进计划](../plans/2026-08-04-yusi-agent-product-roadmap.md)
- 前置评测契约：[Yusi Agent 产品事件与评测基线](2026-08-04-yusi-agent-product-event-contract.md)
- 相关基线：[LifeGraph promotion H2 回放基线](2026-08-17-yusi-lifegraph-promotion-evaluation-design.md)

## 1. 目标与决策

本切片采用方案 A：重要人物仍然是 `User -> Person` 关系事实，不向 Person 实体增加
重要性或确认状态缓存字段。记忆中心只从 `LifeGraphRelation` 的当前行投影两个低敏
元数据字段：

- `relationToUser`：与当前用户的关系类型，例如 `PARTNER_OF`、`FAMILY_OF`；无直接
  User-Person 关系时为 `null`。
- `relationOrigin`：该关系的确认来源，只有 `AUTO` 或 `MANUAL`；没有关系时与
  `relationToUser` 同时为 `null`。

同时把已经参与匹配画像决策的 `importance` 在记忆中心前端以只读方式展示。该字段不由
用户编辑，也不改变本切片已经完成的 importance 写入与排序链路。

本切片唯一的用户消费面是现有 `GET /api/memory/life-graph` 记忆中心。后端 DTO 的
`LifeGraphMemoryItem` 在当前代码中已经有 `importance`，`LifeGraphLifecycleService.toItem`
也已经映射它；缺口在端到端消费层：`frontend/src/lib/api.ts` 的类型和
`LifeGraphMemoryPanel` 尚未把它声明并展示。因此实现时不重复增加 Java 字段，只补齐
关系投影、前端类型和现有卡片展示。

示例响应只表达安全元数据，不代表新增可写能力：

```json
{
  "type": "Person",
  "importance": 0.8,
  "relationToUser": "PARTNER_OF",
  "relationOrigin": "MANUAL"
}
```

关系事实仍由 `life_graph_relation` 表负责，实体字段不复制关系类型或 origin。关系删除
后下一次读取重新从关系表计算投影，不能从缓存的 Person 字段返回旧值。

## 2. 消费面盘点

| 消费面 | 当前入口与现状 | 当前缺口 | 本切片决策 |
| --- | --- | --- | --- |
| 记忆中心 | `LifeGraphLifecycleService.list -> toItem`，由 `/api/memory/life-graph` 返回实体摘要、来源、置信度、生命周期和匹配授权；卡片已有人物类型，但没有与用户的直接关系说明，前端也没有展示 `importance` | 用户能看到“有一个人物记忆”，却不能知道它与自己的关系事实和确认来源；匹配已消费的 importance 也不可见 | 唯一修改面。增加只读关系投影，Person 卡片展示关系类型与 AUTO/MANUAL 来源，并展示 importance |
| 社区图谱 | `LifeGraphDataService` 通过 `GraphSnapshotDTO` 返回图节点与边，已有独立图谱查询和可见性边界 | 图谱消费的是全图/局部图边，不是 Person 记忆卡片；没有把记忆中心 DTO 的关系投影作为依赖 | 保持不变，不新增字段、不改变图分页或展示顺序 |
| 匹配画像 | `LifeGraphEntityRepository.findMatchableTopByUserId -> MatchProfileAssemblerImpl` 已在上一切片消费 `LifeGraphEntity.importance`，并受 `matchAllowed`、隐藏、过期和 User 过滤约束 | 画像是决策消费，不是透明度展示；本切片不把关系文本或 relation origin 注入画像 | 保持不变；不修改实体排序、画像摘要、人物重要性字段或中期记忆衰减链 |
| LifeGraphTool | `LifeGraphTool -> LifeGraphQueryService.localSearch` 为 Agent 提供局部图上下文，按自己的可见性、关系可用性和检索限制输出图信息 | 工具上下文不是用户记忆中心；把 `relationToUser` 加入工具输出会扩大契约和敏感面，工具日志安全收敛另有路线项 | 保持不变；不新增 relation projection 到工具结果，不改变查询、证据或日志行为 |

这项工作属于 Phase 1 已承诺的记忆透明度补全：路线图已经承诺记忆中心展示安全摘要、
来源元数据、生命周期和使用范围，也要求对 LifeGraph 提供同等透明度。Person 的直接
关系类型、确认来源和已参与决策的 importance 都是现有事实或已有决策字段的安全元数据，
补齐它们不会新增页面、操作、数据能力或新的用户可见工作流。因此它是现有记忆中心
透明度承诺的完整化，与“上线前不新增用户可见功能”原则兼容，而不是新增功能面。

## 3. 数据契约与投影边界

### 3.1 字段契约

`LifeGraphMemoryItem` 的用户可见字段定义为：

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `importance` | `number` / `Double` | `0..1` 的只读决策元数据。后端沿用当前 `null -> 0.5` 的 DTO 兼容默认值；PATCH 请求不接受此字段 |
| `relationToUser` | `string \| null` / `String` | 仅对当前用户拥有的 Person 实体返回直接 User-Person 关系类型；返回规范化的大写 relation code |
| `relationOrigin` | `"AUTO" \| "MANUAL" \| null` / `String` | 与选中的关系事实的 `LifeGraphRelation.origin` 一致；无 `relationToUser` 时必须为 `null` |

这三个字段都是输出字段。`UpdateLifeGraphMemoryRequest` 不增加关系或 importance 字段，
现有实体删除、隐藏、有效期和匹配授权操作也不改变关系事实的写入语义。

### 3.2 事实读取与语义方向

关系表同时保存物理端点 `sourceId/targetId` 和语义端点
`semanticSourceId/semanticTargetId`。投影必须按语义端点识别方向；语义字段为空时，
使用对应物理端点作为兼容回退，沿用 `LifeGraphBuildServiceImpl` 和
`LifeGraphQueryService` 的现有规则。

对当前 `userId` 的读取流程为：

1. 从 `LifeGraphEntityRepository` 取得当前用户自己的 User 实体（类型 `User`、
   `LifeGraphConstants.USER_ENTITY_NORM`，当前规范值为 `__user__`）。找不到该实体时，
   所有 Person 的关系投影均为空。
2. 从 `LifeGraphRelationRepository.findByUserId(userId)` 读取当前租户的关系事实；不
   使用无 user 条件的查询，也不根据显示名称或 nameNorm 跨用户猜测端点。
3. 仅接受同时满足以下条件的关系：
   - `relation.userId` 是当前用户；
   - 关系类型属于 `LifeGraphRelationType.isPersonRelation()` 的人物关系集合；
   - 一个语义端点是当前用户自己的 User 实体，另一个语义端点正好是当前 Person 实体；
   - Person 实体由当前用户的实体查询取得。
4. 只把关系的 type 和 origin 映射到 DTO。`confidence`、`weight`、`props`、
   `evidenceDiaryId`、relation evidence、snippet、关系正文和任何原始 mention 都不进入
   `LifeGraphMemoryItem`。

这样既支持 `User -> Person`，也支持关系事实语义方向为 `Person -> User` 的情况，但
不会把 Person -> Person 的关系、Person 的其他邻居或 User 的其他关系扩散到人物卡片。
`REJECTED_RELATIONS` 和非人物关系不会成为人物关系投影；当前 promotion 的关系白名单
和确认逻辑保持原样。

### 3.3 多条关系的确定性规则

一个 Person 可能同时存在多个 User-Person relation type，而 DTO 采用单值字段。实现
选择一个“代表关系”，规则固定为以下字典序：

```text
originPriority DESC  // MANUAL 优先于 AUTO，人工确认是更强的事实信号
updatedAt DESC       // 同一来源下取最近更新的事实，null 最后
type ASC             // 时间相同时按规范化 relation code 稳定排序
id ASC               // 完全同分时使用数据库主键稳定兜底
```

该策略不会伪造或合并关系：只展示一对来自同一真实关系行的 type/origin。选择 MANUAL
优先是为了让“已确认”不会被后续自动抽取的同人物关系遮住；时间、type 和 id 让 H2、
MySQL 以及不同查询返回顺序下的结果可重复。把全部关系改成数组、展示关系证据或提供
关系编辑能力属于后续独立设计，不在本切片扩大 DTO。

## 4. 低敏与隔离红线

- API 只返回 relation type 和 `AUTO/MANUAL` origin，不返回 evidence snippet、关系
  正文、关系 props、证据日记正文、mention 原文、Prompt、工具参数/结果或密钥。
- 关系和端点都按当前 `userId` 验证；跨用户的 User、Person、relation 或同名实体不能
  产生投影。测试必须包含一个跨用户关系反例。
- `importance` 是数值元数据，不返回其计算过程、模型输入、实体摘要以外的来源正文，
  也不允许由前端编辑。
- 回放 fixture 只使用固定、脱敏的实体 key 和关系 code。Trace、报告和日志只写固定
  case id、断言代码、计数和 PASS/FAIL，不写用户 query、记忆正文、证据 token、Prompt
  内容、工具参数/结果、异常正文或堆栈。
- 关系投影不写入实体、缓存、向量库或新的数据库列；每次读取以关系表当前状态为准。

## 5. 真实 H2 回放

新增独立的 `lifegraph-memory-relation-v1` 回放测试，沿用现有
`OfflineEvaluationReportWriter` 和 `target/evaluation/*.json` 目录约定。输入复用已经
脱敏的 `src/test/resources/evaluation/lifegraph-promotion-v1-fixtures.json`，使用其原生
`EVAL-MEM-003-B` 场景身份 `fixture-user-promotion-b` / `fixture-diary-promotion-b`，不做
用户或 source ID 重映射。由于 `LifeGraphPromotionFixtureLoader` 会校验完整 suite 和
`fixture-diary-promotion-[a-z0-9-]+` 格式，测试先完整加载 suite，再按 case/scenario ID
精确选择 B；只额外 seed 本回放所需的投影实体与关系行，并用精确实体 id 定位断言。
删除回放使用同一用户下独立的 deletion-only Person，避免与双关系选择场景相互干扰。

回放流程：

```text
既有脱敏 fixture 的固定 extraction（原生 B 身份）
  -> H2 中额外 seed 投影所需 user/person/entity 数据
  -> 预置真实 User-Person MANUAL 关系
  -> LifeGraphLifecycleService.list
  -> 对照 life_graph_relation 当前行的 type/origin
  -> delete/flush 该关系
  -> 再次 list，关系投影必须消失
  -> 另一个 user 的同名 Person/关系反例
  -> OfflineEvaluationReportWriter
  -> target/evaluation/lifegraph-memory-relation-v1-report.json
```

必须有以下正向断言：

1. H2 读取到的 Person 卡片 `relationToUser` 和 `relationOrigin` 与关系表当前事实完全
   一致，语义方向反转的关系也能投影到同一个 Person。
2. `importance` 与实体当前值一致，且非 Person 或没有直接 User-Person 事实的实体的
   关系字段为空。
3. 从 H2 删除关系并 flush 后再次调用记忆中心服务，`relationToUser` 和
   `relationOrigin` 同时为 `null`，证明不存在 Person 字段或读缓存漂移。
4. 跨用户关系和同名实体不产生投影；多条关系按 MANUAL、更新时间、type、id 规则稳定
   选择。
5. DTO 与报告相关对象中不存在 evidence snippet 或关系正文字段。

报告固定使用 schema version 1、runner version `v1`，suite id 为
`lifegraph-memory-relation-v1`。每个 CaseResult 的 `versions.prompt` 必须正向断言恰好
为：

```json
{"key":"fixture","version":"fixture-v1","locale":"zh-CN"}
```

低敏扫描先删除 `versions.prompt` 字段，再扫描报告值中的
`evidence-token-`、`rawtext`、`plaincontent`、`toolarguments`、`toolresult`、
`secret`、`password` 等禁词，避免把必需的字段名 `prompt` 自身误报。actualSummary
只允许断言计数、固定布尔结果和固定 violation code，不写实体名称、关系正文或异常文本。

## 6. 范围与非目标

### 6.1 本切片范围

- `LifeGraphMemoryItem` 增加 `relationToUser`、`relationOrigin` 输出字段；保留并补齐
  已存在的 `importance` 端到端展示。
- `LifeGraphLifecycleService` 从当前用户的关系表事实构造 Person 关系投影，包含语义
  方向、跨用户隔离、单值确定性选择和删除后实时同步。
- `LifeGraphMemoryPanel` 在 Person 卡片展示关系类型和确认来源，在现有安全元数据区
  展示 importance；中英文 i18n 同步补齐。
- 后端单元测试、真实 H2 回放、低敏报告和默认 Maven 测试接入。

### 6.2 非目标

- 不增加 Person 实体的 `importance`、`relationToUser`、`relationOrigin` 或任何其他
  缓存字段，不增加 migration、表、索引或持久化 projection。
- 不改变 `LifeGraphPromotionPolicy`、`findConfirmedImportantPersons`、抽取结果、
  `REJECTED_RELATIONS`、证据要求、关系 origin 写入或 revision 幂等行为。
- 不修改社区图谱 `GraphSnapshotDTO`、全图分页、图 BFS、名称搜索、Top50、
  `LifeGraphBuildServiceImpl.buildKnownEntities`、社区洞察、合并候选、情绪触发或 Prompt
  已知实体排序。
- 不修改 `findMatchableTopByUserId`、`MatchProfileAssemblerImpl`、匹配画像文本和
  `MidTermMemory.calculateDecayedImportance`；importance 只在记忆中心透明展示。
- 不把 relation projection 加入 LifeGraphTool、MemorySearchTool、Agent prompt、
  Milvus、Redis、OSS 或任何 Trace/log 输出。
- 不新增关系编辑、关系确认按钮、关系证据展开、全部关系数组、跨用户社区关系或新的
  用户可见页面；不启动 post-release expansion backlog 的任何条目。

### 6.3 上线前 MySQL 回归清单

上线前必须在目标 MySQL 版本执行一次与 H2 回放等价的关系投影回归，确认以下行为
一致后才能归档为上线证据：

- `LifeGraphRelationRepository.findByUserId` 的关系读取与内存中的代表关系选择顺序
  不依赖数据库默认返回顺序；
- `semantic_source_id` / `semantic_target_id` 任一为空时，按对应物理端点回退，且
  当前用户与 Person 的语义方向判断不漂移；
- `origin` 的 `AUTO` / `MANUAL` 字符串枚举映射与 H2 一致，删除关系后再次读取的
  两个投影字段同时为空。

H2 通过不能替代目标 MySQL 回归；本切片只记录检查项，不启动 MySQL 或应用服务。

## 7. 验收标准

- Java 单元测试覆盖正向关系、反向语义关系、无关系、非人物关系、跨用户端点、多人
  物关系确定性、importance 透传和敏感字段不外泄。
- 真实 H2 回放报告在 `target/evaluation/lifegraph-memory-relation-v1-report.json`
  生成，所有 case 为 PASS，关系删除后的二次读取断言通过，`versions.prompt` 正向对象
  断言通过。
- `frontend` 的 API 类型包含 nullable relation projection、`AUTO/MANUAL` origin 和
  importance；Person 卡片只展示安全关系元数据，中英文构建通过。
- `pnpm test`、`pnpm run build` 和全量 `.\mvnw.cmd -q test` 通过；不启动服务。
- 完成后提交本切片，停下来等待验收；人物字段改造是否独立切片在本切片验收后再决定。
