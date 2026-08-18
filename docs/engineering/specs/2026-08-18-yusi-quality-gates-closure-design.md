# Yusi 质量门槛收尾设计

- 状态：待用户评审确认
- 日期：2026-08-18
- 所属阶段：Phase 4 评测、回放与质量回归
- 前置路线图：[Yusi Agent 产品与工程演进计划](../plans/2026-08-04-yusi-agent-product-roadmap.md)
- 评测契约：[Yusi Agent 产品事件与评测基线](2026-08-04-yusi-agent-product-event-contract.md)
- 上线后边界：[Yusi 上线后扩展 Backlog](../plans/2026-08-17-yusi-post-release-expansion-backlog.md)

## 1. 目标

本设计覆盖路线图 Phase 4 后续推进顺序中的“质量门槛收尾”四个确定性子项：

1. 对话评测集的离线协议基线；
2. Timeline 来源修改后的真实 H2 重建回放；
3. 匹配召回、理由、连接生命周期和强负面排除评测集；
4. 关键指标的统一回归门槛。

目标是在不启动应用服务、不连接真实 MySQL/Redis/Milvus、不调用远程模型、不新增用户可见
功能的前提下，继续沿用现有 `OfflineEvaluationReportWriter`、`target/evaluation/*.json`、
默认 Maven 测试和 CI artifact，形成可重复的低敏质量证据。

这四项是四个独立切片。当前只设计总边界，实施时按对话 -> Timeline 重建 -> 匹配 -> 统一
门槛的顺序逐刀执行；每刀独立全量测试、提交后停止，下一刀重新评审。未获对应切片确认前
不修改生产代码或测试代码，不启动服务。

## 2. 勘察结论

### 2.1 已有质量证据

当前默认测试已经生成以下低敏报告，报告统一使用 schema version `1` 和四类版本槽位：

| 套件 | 当前状态 | 已覆盖 | 本设计的处理 |
| --- | --- | --- | --- |
| `lifegraph-promotion-v1` | PASS，3 cases | 固定抽取结果、promotion 边界、来源证据、人物关系禁止扩散 | 作为已有认知基线，保持回归 |
| `lifegraph-timeline-v1` | PASS，4 scenarios | 事件资格、来源 revision、重复贡献、删除后 Timeline | 保持原报告，新增重建使用独立套件 |
| `memory-lifecycle-v1` | PASS，3 cases | 中期记忆隐藏/过期/合并、授权、删除和跨用户隔离 | 作为删除与隐私基线，保持回归 |
| `lifegraph-importance-v1` | PASS，1 case | `LifeGraphEntity.importance` 在两个匹配消费入口的排序 | 作为匹配画像输入基线，保持回归 |
| `lifegraph-memory-relation-v1` | PASS，1 case | Person 关系投影、origin、importance 和删除同步 | 作为记忆透明度基线，保持回归 |

路线图中的广义“记忆评测集”仍未完成。已有 promotion/lifecycle 套件不能宣称覆盖实体/关系
抽取质量、冲突识别、有效期策略等全部记忆质量，因此本设计不修改该 checkbox，也不把四个
新套件包装成完整记忆评测集。

### 2.2 对话入口与真实能力边界

- `AiController.chatStream` 是当前聊天流的公开后端入口；`Assistant` 通过 LangChain4j
  `TokenStream` 输出响应，工具事件由控制器以低敏 `AgentStreamEvent` 投影。
- `ContextBuilderService` 是确定性上下文入口，负责 system message、Persona、近期
  `MidTermMemory`、认知冲突、记忆检索指导和关系阶段。其 `MidTermMemory` 衰减排序已有
  独立正向测试，本设计只读取，不改变衰减逻辑。
- `PersistentChatMemoryStore` 会从真实 H2 的聊天/画像/记忆行重建上下文；测试 profile 的
  Redis 由 `TestInfrastructureConfig` 替换，不访问外部 Redis。
- 当前默认 Maven/CI 没有稳定的远程模型凭据，也不应把模型网络调用引入必过的测试套件。
  因此默认对话套件验证生产上下文组装、记忆可见性、关系阶段、工具生命周期和低敏 SSE
  契约；它不把测试替身返回的文本当成真实模型的语气或幻觉评分。

### 2.3 Timeline 重建入口

`LifeGraphBuildServiceImpl.upsertFromDiary`/`LifeGraphTaskBatchService.processSingleTask` 对同一
来源先撤销旧来源贡献再写入新抽取结果；`LifeTimelineService.getLifeChapters` 每次从当前可见
Event 行读取、过滤无日期事件并重新聚类。已有回放只覆盖一次 upsert 和 delete，缺少“旧来源
结果不再出现、新来源结果出现、再删除后 Timeline 清空”的修改回放。因此新增独立的
`lifegraph-timeline-rebuild-v1`，不改动 `lifegraph-timeline-v1` 的 fixture、scenario 数或报告。

### 2.4 匹配与连接入口

- `LifeGraphEntityRepository.findMatchableTopByUserId` 和 `MatchProfileAssemblerImpl` 已消费
  `LifeGraphEntity.importance`，现有 importance 回放会保持绿。
- `MatchServiceImpl` 使用 Milvus hybrid recall、候选优先级、ChatModel 精排、分数阈值和
  `SoulMatch` 持久化；Milvus/Embedding/ChatModel 在离线回放中必须是 Mockito 替身，不能
  连接真实服务。
- `SoulConnectionLifecycleService` 和 `MatchFeedbackService` 是可用的确定性决策入口：
  接受、双方开始互动、双向 `DEEP_INTERACTION`、举报/拉黑/不继续和强负面排除都能用真实
  H2 的 `SoulMatch`、`SoulConnection`、`SoulConnectionEvent`、`ProductEvent`、
  `MatchFeedback` 行回放。
- 生产事件有 `match.recommended`、`connection.accepted`、连接反馈和连接状态事件，但没有
  `match.viewed`。事件契约定义的接受率分母是 `match.viewed`，所以本切片不能用推荐数或
  `SoulMatch` 查询数冒充 viewed，也不能输出伪造的接受率。

## 3. 方案选择

### 3.1 方案 A：一个跨域总回放测试

把对话、Timeline、匹配、指标全塞入一个 `@SpringBootTest` 和一个 fixture，最后统一写一份
报告。

不采用。四个领域的隔离边界不同：对话需要 TokenStream/控制器替身，Timeline 需要来源任务
和真实 H2，匹配需要 Milvus/ChatModel 替身和连接事实。跨域 fixture 会放大清理状态、测试顺序、
报告失败定位和敏感字段误入的风险，也无法保证某个子系统单独回归时仍有清晰报告。

### 3.2 方案 B：独立套件 + 共享门槛策略（采用）

每个领域使用独立版本化 fixture、loader、replay test 和报告；共享 `OfflineEvaluationReportWriter`
和一个测试侧 `QualityGatePolicy`，由各套件在写报告前执行统一的“case 全通过、断言数量不降、
敏感计数为零、版本槽位完整”策略。Timeline 重建和匹配事实使用真实 H2；外部模型/向量依赖
统一使用替身；对话使用真实上下文/控制器边界加确定性替身。

采用理由：报告可独立归档和定位，H2 可以证明业务表当前事实，替身不会把外部服务不稳定性
带入默认 Maven，且不会复制生产聚合逻辑。关键门槛由固定 case 数、固定最小断言数和数值型
actual summary 锁住，删除一个 case 或降低安全断言会让测试直接失败。

### 3.3 方案 C：默认 Maven 调用真实模型做自由文本评分

不采用。它需要密钥、网络、模型版本和 judge 策略，结果非确定且可能把 query、Prompt、
记忆正文或模型输出带入日志/报告；不符合本项目默认测试和低敏边界。未来若需要真实模型
对照，应另建非默认、受控、脱敏的评测执行器，并先过独立设计评审。

## 4. 总体架构与数据流

```text
脱敏 fixture JSON
    -> EvaluationFixtureRedLineValidator
    -> 严格 typed loader（ID、版本、case 形状）
    -> 领域 replay
       ├─ 对话：H2 上下文 + Assistant/TokenStream 替身 + AgentStreamEvent
       ├─ Timeline：真实 H2 + LifeGraphBuildService + LifeTimelineService
       └─ 匹配：真实 H2 连接事实 + Milvus/Embedding/ChatModel Mockito 替身
    -> 固定 violation code / 数值 actualSummary
    -> QualityGatePolicy
    -> OfflineEvaluationReportWriter
    -> target/evaluation/*.json + JUnit PASS/FAIL + CI artifact
```

所有 replay 都在测试进程内运行，不启动 Web、gRPC、Redis、Milvus、MySQL、OSS 或模型服务。
`@SpringBootTest` 只加载 test profile 的 Spring 容器；`server.port=0` 不代表启动一个供用户
访问的服务，也不允许测试通过 HTTP 调用自身。

每个 case 的失败只写固定 violation code，例如 `FIXTURE_INVALID`、`CONTEXT_MEMORY_BOUNDARY`、
`TIMELINE_REBUILD_OLD_RESIDUAL`、`MATCH_NEGATIVE_NOT_EXCLUDED`。不得把异常 message、堆栈、
query、记忆正文、Prompt、工具参数/结果或用户实体写进报告、日志或 assertion message。

## 5. 四个独立切片

### 5.1 对话协议基线：`chat-quality-v1`

资源和报告：

- fixture：`src/test/resources/evaluation/chat-quality-v1-fixtures.json`；
- 报告：`target/evaluation/chat-quality-v1-report.json`；
- case：沿用事件契约的 `EVAL-CHAT-001/002/003` 和 `EVAL-TOOL-001`；
- 测试：`src/test/java/com/aseubel/yusi/evaluation/chat/ChatQualityEvaluationTest.java`。

fixture 只保存 `scenarioId`、虚构用户/记忆 key、`inputKind`、可用认知范围、允许工具、
期望的固定 policy code 和数值期望，不保存 `query`、消息正文、记忆摘要正文、Prompt 或
工具参数/结果。测试将 `inputKind` 映射为进程内合成输入，合成值不会进入日志、数据库、报告。

四个场景的确定性覆盖：

| 场景 | 真实入口 | 断言 |
| --- | --- | --- |
| 无历史询问长期偏好 | H2 `ContextBuilderService`/`PersistentChatMemoryStore` | 没有可用记忆时仍保留关系阶段的克制/不捏造指导，不产生稳定画像正文 |
| 有支持和不支持的历史事实 | H2 `MidTermMemory`/Persona 可见性查询 | 只把当前可用范围放进 context；隐藏/过期/跨用户行不进入 context，正向可见数据存在 |
| 新旧认知冲突 | H2 `CognitiveConflict` + context builder | 未解决冲突被标记供 Agent 注意，不把冲突静默变成单一事实；报告只记计数 |
| 工具失败/边界 | 真实 `AiController` + mock `Assistant`/`TokenStream` | 工具事件只有本地 ID、工具名、来源、成功状态、耗时；不发送参数/结果；失败后 run 有安全终态 |

五类对话指标在默认离线测试中的可计算映射为：记忆引用正确性使用“可见正向记忆存在且受限
记忆不存在”的 context policy count；语气一致性使用 Persona style 与关系阶段 policy count；
幻觉使用无事实时的 no-unsupported-claim policy count；隐私边界使用跨用户/隐藏/过期泄漏
计数；工具使用边界使用参数/结果暴露计数和失败终态计数。它们是可观察的策略输入/安全协议
门槛，不是对自由文本的真实模型评分。报告需要显式带有固定的
`semanticModelScoreAvailable=false` 或等价数值/布尔字段。
这保留了诚实的能力边界：四类对话场景已建立可回放的输入/工具安全基线，但不能宣称已经
完成真实模型语气质量和幻觉率评测。

### 5.2 Timeline 重建：`lifegraph-timeline-rebuild-v1`

不修改已有 `lifegraph-timeline-v1`。新增一个只有脱敏合成事件的 fixture 和独立测试，使用
真实 H2、`LifeGraphTaskBatchService`、`LifeGraphBuildService` 与 `LifeTimelineService`。

单个场景依次回放：

1. 来源 revision 1 写入 `fixture-rebuild-event-old`，Timeline 出现旧节点；
2. 同一 Diary 来源 revision 2 写入 `fixture-rebuild-event-new`，旧节点及旧来源证据消失，
   新节点出现，Timeline 只按当前 Event 行重建；
3. 删除同一来源，当前 Event/证据/关系残留为零，Timeline 节点数变为零；
4. 断言来源修改前、修改后、删除后的正向结果和跨用户隔离，报告只保存节点/残留计数。

抽取器仍是固定的 8 参 Mockito stub，不能调用 LLM。重建验证的事实来源是 H2 行和
`LifeTimelineService` 查询结果，而不是测试内复制的聚类实现。

### 5.3 匹配评测集：`match-quality-v1`

新增脱敏 fixture 和独立 `@SpringBootTest`，按“算法替身边界 + H2 连接事实”拆为三个场景：

| 场景 | 回放 | 质量证据 |
| --- | --- | --- |
| `EVAL-MATCH-001-A` | 用 Milvus/Embedding/ChatModel 替身返回固定召回与精排结果，走实际匹配候选和推荐事实写入 | 期望召回覆盖率、推荐理由字段覆盖、推荐事件低敏 payload、跨用户 candidate 不泄漏 |
| `EVAL-MATCH-001-B` | 真实 `SoulMatch`/`SoulConnection`/`MatchFeedback`/`ProductEvent` 回放双方接受、互动和两侧 `DEEP_INTERACTION` | `STARTED` 允许持续互动，双方深度反馈后才是 `MUTUAL_RESONANCE`，连接事件顺序与事实一致 |
| `EVAL-MATCH-001-C` | 预置已过冷却窗口的历史 match 和强负面 feedback，再执行实际匹配路径 | 该 pair 不重新推荐；`hasStrongNegativeSignal` 和连接安全终态均为正向对照 |

报告只输出候选/推荐/事件/连接/反馈的数量、固定状态计数和 reason coverage 数，不输出用户
ID、画像文本、理由正文、letter、Prompt、模型请求或向量数据。Milvus、Embedding、ChatModel
替身返回的 token 只存在内存，不能出现在日志或报告。

接受率的处理：报告可记录 `recommendedCount`、`acceptedCount` 和 `viewedCount=0`，并把
`acceptanceRateAvailable=false` 写成机器可读状态；禁止计算 `accepted / recommended` 或将
`SoulMatch` 查询数当 viewed。真正补齐接受率需要独立的 `match.viewed` 生产事件切片，本设计
不新增该事件、不修改生产事件枚举、不勾选对应质量门槛。

### 5.4 统一回归门槛

新增测试侧 `QualityGatePolicy`，不修改生产代码和 `OfflineEvaluationReportWriter`。每个新
套件在写报告前调用相同的通用门槛，再调用本领域数值门槛；不依赖 Surefire 测试类执行顺序去
读取另一个测试类刚生成的 `target` 文件。

通用门槛：

- 预期 suite ID、case ID、fixture/expected version 必须完整；
- 所有 case `status=PASS`，`passedAssertionCount == assertionCount`，断言数不能低于设计值；
- `versions.model/prompt/retrieval/ranking` 全部存在，`versions.prompt` 必须正向等于
  `{key: "fixture", version: "fixture-v1", locale: "zh-CN"}`；
- 报告 `actualSummary` 只允许数字、布尔值和固定枚举字符串；
- 隐私、安全、删除传播、来源正确性和跨用户隔离计数必须为零违规，不能用平均分抵消；
- 任何 fixture 解析失败、H2 回放异常、报告生成失败都让 JUnit 失败并写固定 code。

领域门槛：

| 领域 | 必过门槛 |
| --- | --- |
| 对话 | 4 cases 全通过；memory reference、tone policy、no-unsupported-claim、context isolation、冲突显式化和工具参数/结果外泄门槛全通过；泄漏计数为 0；`semanticModelScoreAvailable=false` 必须存在 |
| Timeline 重建 | old contribution residual=0、new contribution present=1、post-delete timeline node count=0、source residual=0 |
| 匹配 | 召回期望集合全部命中；推荐 reason coverage=100%；双方深度互动才进入共鸣；持续互动状态有效；强负面 pair 后续推荐数=0 |
| 既有基线 | promotion、timeline、memory lifecycle、importance、relation projection 报告继续 PASS；任何一个旧套件失败都阻断全量 Maven |

指标的分母、窗口和实现映射沿用事件契约。`connection.accepted` 是当前实现中“接受动作”的
事实事件；`match.viewed` 缺失导致接受率只做缺口报告，不进入 PASS 数值门槛。小样本只报告固定
分子/分母和是否可计算，不把它们解释为线上统计结论。

## 6. 报告、fixture 与低敏红线

### 6.1 fixture

所有新 fixture 必须先经过共享 `EvaluationFixtureRedLineValidator`，再经过领域 loader：

- 只允许 `fixture-user-*`、`fixture-memory-*`、`fixture-source-*`、`fixture-event-*` 等虚构 ID；
- 摘要、关系证据和模型替身结果只使用 `*-token-*` 或固定枚举，不使用自然语言正文；
- 禁止字段继续包含 `plainContent`、`rawText`、`prompt`、`toolArguments`、`toolResult`、
  `secret`、`password`、`content`；
- 不保存用户 query、记忆正文、Prompt、工具参数/结果、密钥、异常正文或可还原个人信息；
- loader 拒绝未知字段、重复 case、缺场景、跨用户 owner 不一致和不符合 suite version 的值。

### 6.2 report

每个套件都使用 `OfflineEvaluationReportWriter.write(...)` 写到 `target/evaluation`，不新增
持久化表，不提交 `target`。`actualSummary` 只保存计数、布尔门槛和固定状态，例如
`oldResidualCount`、`reasonCoveragePassCount`、`acceptanceRateAvailable`；不保存实体 key、
消息文本、关系正文、Prompt 或工具数据。

敏感扫描必须先从内存中的 JSON 副本移除 `versions.prompt` 字段，再扫描
`evidence-token-`、`rawText`、`plainContent`、`toolArguments`、`toolResult`、`secret`、
`password` 等内容。不能因为 JSON 必然存在字段名 `prompt` 就把合法的版本槽位误报为违规。
`versions.prompt` 必须用正向断言精确检查 fixture 版本对象。

### 6.3 CI

不修改 CI。现有 workflow 的 `target/evaluation/*.json` wildcard 会自动归档三份新报告和
已有报告；默认 `./mvnw test` 会执行所有评测门槛。报告生成时间 `generatedAt` 是运行元数据，
不用于语义回归比较。

## 7. 非目标与隔离清单

本设计明确不做以下内容：

- 不修改 `src/main/java`、数据库 migration、Controller API、前端页面或生产事件枚举；
- 不把测试替身输出当成真实 LLM 自由文本质量分；真实模型语气、幻觉率和 Prompt/模型对照
  需要另一个受控评测执行器，不进入默认 Maven；
- 不补 `match.viewed` 事件，不在当前切片计算接受率；
- 不修改 `MidTermMemory.calculateDecayedImportance`、聊天近期状态排序或其它中期记忆消费链；
- 不扩展 LifeGraph 广义记忆评测集中的抽取质量、冲突识别、有效期策略等未覆盖项；
- 不改变既有 Timeline v1、promotion、lifecycle、importance、relation projection fixture
  或报告契约；
- 不触碰路线图列出的展示/运营排序入口：全图分页、名称搜索、Top50、社区洞察、合并候选、
  情绪触发；这些不是当前匹配决策消费切片的范围；
- 不启动 post-release backlog 的主动陪伴深化、持久化 Agentic Runtime、评测与训练深化或
  任何新用户可见功能；
- 不把默认测试日志清理切片与本切片合并，也不把本切片当作 Phase 5 生产日志安全完成证明。

## 8. 验收与路线图状态

每个子切片都必须满足：

1. fixture loader、评测回放和报告 schema 测试通过；
2. 领域聚焦测试通过；
3. `.\mvnw.cmd -q test` 全量通过，所有既有和新增报告为 PASS；
4. changed-files 自检确认没有超出该子切片的生产/前端/数据库范围；
5. 低敏扫描、`versions.prompt` 正向断言和固定 case/断言数门槛通过；
6. 自查 roadmap 对应 checkbox 后独立提交，提交后停下等待验收。

四个子切片全部完成后，才可评审是否勾选 Phase 4 的对话、Timeline、匹配和关键指标条目。
广义记忆评测集仍保持未勾选；`match.viewed` 事件缺口和真实模型语义评测残余风险必须带入
上线 GO/NO-GO 记录，不能隐藏在“质量门槛 PASS”字样后。
