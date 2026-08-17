
# LifeGraph importance 决策消费设计

- 状态：待用户评审确认
- 日期：2026-08-17
- 所属阶段：Phase 4，importance 决策消费切片
- 前置路线图：[Yusi Agent 产品与工程演进计划](../plans/2026-08-04-yusi-agent-product-roadmap.md)
- 前置评测契约：[Yusi Agent 产品事件与评测基线](../specs/2026-08-04-yusi-agent-product-event-contract.md)
- 前置回放设计：[LifeGraph promotion H2 回放基线](2026-08-17-yusi-lifegraph-promotion-evaluation-design.md)

## 1. 目标

本切片把已有的 LifeGraphEntity.importance 接入唯一指定的决策消费链：

~~~~text
LifeGraphEntityRepository.findMatchableTopByUserId
    -> MatchProfileAssemblerImpl.buildLifeGraphSummary
    -> MatchProfile.lifeGraphSummary / profileText
~~~~

切片完成后，匹配画像候选不再只按 mentionCount 取样和排序，而是能够优先使用
长期价值更高的实体；同一重要性下仍以提及次数作为确定性的支持信号。现有
matchAllowed、hidden、validUntil 和排除 User 节点的边界保持不变。

本切片还用已完成的 lifegraph-promotion-v1 场景 B 做真实 H2 回放对照：复用同一份固定
抽取结果，但把回放身份映射到独立的 fixture-user-importance-b /
fixture-diary-importance-b；它先经过现有 promotion 和 BuildService 落库，再在测试夹具层
显式开启匹配授权，最后检查真实查询结果和真实 MatchProfile 输出。人物字段改造只在本
切片验收后另行评估。

## 2. 勘察结论

### 2.1 当前 LifeGraph 消费

LifeGraphEntity 已有一等字段 importance，默认值为 0.5。LifeGraphBuildServiceImpl
在抽取实体创建和更新时会写入并限制到 [0, 1]；自动创建的实体默认
matchAllowed=false，因此匹配授权仍是独立的使用范围门槛。

当前匹配候选查询 findMatchableTopByUserId 使用：

~~~~sql
ORDER BY mentionCount DESC, updatedAt DESC
~~~~

当前 MatchProfileAssemblerImpl.buildLifeGraphSummary 的行为是：

1. 请求最多 50 个 matchable、可见且未过期实体；
2. 排除 EntityType.User；
3. 按固定类型优先级 Topic > Event/Person > Emotion > Place > Item/Work；
4. 在类型优先级相同的情况下按 mentionCount DESC；
5. 取前 6 个实体写入长期结构摘要。

因此，当前 importance 只影响写入字段，不影响候选窗口或最终画像顺序。

### 2.2 中期记忆边界

MidTermMemory.importance 已是活的消费链。MatchProfileAssemblerImpl 的
calculateDecayedImportance 使用 importance 和创建时间计算 14 天半衰期，并以该值
排序近期匹配上下文。这个行为属于已完成的中期记忆生命周期闭环，本切片不修改：

- 不修改 MidTermMemoryRepository.findMatchableByUserId；
- 不修改 buildMidMemorySummary；
- 不修改 calculateDecayedImportance、默认值或衰减周期；
- 增加一个正向对照断言，证明高衰减后价值的旧记忆不会压过当前高价值记忆，且匹配画像仍
  正常包含允许使用的中期记忆。

## 3. 范围与非目标

### 3.1 本切片范围

- 修改 LifeGraphEntityRepository.findMatchableTopByUserId 的排序键；
- 修改 MatchProfileAssemblerImpl.buildLifeGraphSummary 的实体比较器；
- 为 importance 与 mentionCount 的边界和稳定性增加单元测试；
- 复用 lifegraph-promotion-v1-fixtures.json 的 EVAL-MEM-003-B 抽取结果，但使用独立的
  fixture-user-importance-b / fixture-diary-importance-b 身份，以真实 H2 回放
  promotion -> matchable projection -> MatchProfile；
- 输出独立的 target/evaluation/lifegraph-importance-v1-report.json，接入默认 Maven
  测试和现有 CI target/evaluation/*.json artifact；
- 评估闭环通过后，再单独判断人物重要性字段改造是否需要新切片。

### 3.2 非目标：剩余排序入口

以下入口明确不在本切片。它们会继续使用当前展示或运营语义，后续按用户可感知行为另行
设计和评审，不能因为本切片修改 importance 就顺手改动：

| 入口 | 当前排序/候选行为 | 本切片结论 |
| --- | --- | --- |
| LifeGraphDataService.getFullGraph | findVisibleByUserId 配合 Sort.by(DESC, "mentionCount") 分页返回全图 | 用户可见全图展示顺序不改 |
| LifeGraphQueryService.findSeedEntities | 名称搜索使用 findVisibleByUserIdAndDisplayNameContainingOrderByMentionCountDesc 选择 seed | 搜索结果顺序不改 |
| LifeGraphEntityRepository.findTop50ByUserIdOrderByMentionCountDesc | 既有 Top50 派生查询保留 mentionCount 顺序 | Top50 语义不改 |
| LifeGraphBuildServiceImpl.buildKnownEntities | 为抽取 Prompt 构造已知实体列表时从可见实体中按 mentionCount 取前 50 | 抽取上下文候选不改；本切片不改变 Prompt 或抽取输入 |
| CommunityInsightServiceImpl.detectCommunities | 先按 mentionCount 取前 50 个实体，再构建连通分量，最终社区按 cohesion 排序 | 社区候选截断和社区展示顺序不改 |
| LifeGraphMergeSuggestionService.findCandidates | 先按 mentionCount 取前 50 个实体，再按同类型两两组合和名称相似度生成合并候选 | 合并候选的召回窗口、两两组合和结果排序不改 |
| EmotionTimelineServiceImpl.getEmotionTriggers | 情绪实体由 findAllVisibleByUserIdAndType 按 mentionCount 排序；关联实体集合由可见实体 mentionCount 排序 | 情绪点、触发候选和触发顺序不改 |

上述合并候选和情绪触发属于展示/运营路径，不是本切片定义的匹配画像决策消费；将它们
混入会同时改变用户可见行为和运营候选集合，故明确留到后续切片。

### 3.3 其他非目标

- 不修改 LifeGraphEntity.importance 字段、数据库列、migration、人物字段或人物关系推导；
- 不修改 LifeGraphPromotionPolicy、抽取结果、promotion 白名单、证据要求、来源撤销、
  revision 幂等或 Timeline；
- 不修改 MidTermMemory 衰减链；
- 不修改 matchAllowed、隐藏、过期和跨用户隔离规则；
- 不修改全图、搜索、Top50、社区洞察、合并候选、情绪触发和 Prompt 已知实体排序；
- 不新增生产 API、评测 API、数据库表或 migration；
- 不调用 LLM、Embedding、Milvus、Redis、OSS 或真实用户数据；
- 不把用户 query、记忆正文、Prompt 正文、工具参数/结果、密钥、异常 message、实体摘要
  或 fixture 证据 token 写入 Trace、报告或日志；
- 不启动 post-release expansion backlog 的任何条目。

## 4. 排序语义决策

### 4.1 备选方案

#### 方案 A：字典序，importance 优先，mentionCount 作 tie-breaker（采用）

将两项信号视为有明确业务优先级的排序键：

~~~~text
(importance DESC, mentionCount DESC, updatedAt DESC, id ASC)
~~~~

在匹配画像中保留现有类型优先级作为外层业务键：

~~~~text
(typePriority DESC,
 importance DESC,
 mentionCount DESC,
 updatedAt DESC,
 id ASC)
~~~~

优点是语义可解释、边界清楚、无需训练或调参，并且不会让高频但长期价值低的实体压过
高重要性实体。mentionCount 仍然保留为同重要性实体之间的稳定支持信号。保留现有
typePriority 可以避免本切片意外改变 Topic、Event/Person 等已有画像内容层级。

#### 方案 B：加权分

例如将两项归一化后计算 0.7 * importance + 0.3 * normalizedMentionCount，再按总分排序。
该方案能表达连续折中，但需要定义 mentionCount 的窗口、归一化和权重校准；数据分布变化
会改变历史实体的相对顺序，且“为什么这个高频实体压过高重要性实体”难以解释。当前没有
足够匹配评测集支持权重选择，本切片不采用。

#### 方案 C：仅将 importance 作为 mentionCount 的次级键

即保留 (mentionCount DESC, importance DESC)。它对旧行为最保守，但无法完成本切片的
决策闭环：高频、低重要性的实体仍会持续占据候选窗口和画像前列。本方案不采用。

### 4.2 采用方案的精确定义

#### 候选窗口

findMatchableTopByUserId 使用显式 JPQL 顺序：

~~~~sql
ORDER BY COALESCE(importance, 0.5) DESC,
         COALESCE(mentionCount, 0) DESC,
         updatedAt DESC,
         id ASC
~~~~

生产列已有非空约束和实体默认值；COALESCE 只为历史兼容和 H2/对象边界提供明确默认值。
读取排序不重新计算或改写实体字段。id ASC 是最后的持久化稳定键，避免同分实体因为数据库
默认返回顺序变化而改变候选窗口。

上线前回归清单必须在目标 MySQL 版本执行同一 matchable 查询，确认 COALESCE、DESC/ASC
混合排序、updatedAt 空值行为和 id 稳定兜底与 H2 结果一致；本切片只在 H2 验证，不把本地
H2 通过等同于 MySQL 已验收。

#### 匹配画像顺序

MatchProfileAssemblerImpl 在过滤 User 后使用以下比较器：

~~~~text
typePriority DESC
importance DESC (null -> 0.5)
mentionCount DESC (null -> 0)
updatedAt DESC (null last)
id ASC (null last)
~~~~

importance 字段已由写入链保证位于 [0, 1]；读取端只对 null 做兼容默认，不引入另一套
人物重要性推导。

必须锁定的边界断言：

1. 同一 EntityType、同一 importance 下，mentionCount 较高者在前；
2. 同一 EntityType、同一 mentionCount 下，importance 较高者在前；
3. importance 与 mentionCount 都相同，较新的 updatedAt 在前，仍相同则较小 id 在前；
4. 不同类型仍先遵守既有 typePriority，不会因为本切片把类型层级改成 importance 全局排序；
5. User 实体永远不进入画像摘要；隐藏、过期和未授权实体永远不进入候选。

这意味着，在同一类型内，importance=0.8, mentionCount=1 必须排在
importance=0.5, mentionCount=9 前面；而在 importance 相等时，mentionCount 才能
决定顺序。

## 5. 回放与数据流

~~~~text
lifegraph-promotion-v1-fixtures.json
  -> 读取 EVAL-MEM-003-B（固定 extraction）
  -> 独立 H2 用户 fixture-user-importance-b + fixture-person-b
     + MANUAL User/Person 关系
  -> LifeGraphPromotionPolicy / LifeGraphBuildServiceImpl
  -> H2 实体、关系、证据、mention 落库
  -> 测试夹具显式开启三条已落库实体的 matchAllowed
  -> findMatchableTopByUserId（真实 JPQL 排序）
  -> MatchProfileAssemblerImpl.refreshProfile（真实 MatchProfile）
  -> MidTermMemory 衰减正向对照
  -> 低敏 CaseResult
  -> OfflineEvaluationReportWriter
  -> target/evaluation/lifegraph-importance-v1-report.json
~~~~

场景 B 的抽取结果已有一个 importance=0.8 的人工 fixture-person-b。importance 回放使用
独立的 fixture-user-importance-b 身份，并通过 H2 的人工 User -> Person 关系推导 confirmed
person；promotion 评测自身的 fixture-user-promotion-b 不被复用。回放得到的 Event/Item 仍来自真实
BuildService；测试层只为执行匹配消费显式开启授权，并设置确定性的同类型排序对照：

- fixture-person-b：高 importance、较低 mentionCount；
- fixture-event-b：较低 importance、较高 mentionCount；
- fixture-item-b：作为较低类型优先级的背景实体。

这样可以在不改变 promotion 输入契约的情况下，验证“高重要性人物不会被高频低重要性事件
遮蔽”，并同时验证真实 H2 查询窗口和最终画像摘要。

测试不把实体 key、摘要或 profile 文本放进报告，只在内存中用脱敏 fixture 的位置断言结果，
报告只写断言计数和固定布尔/数值结果。

## 6. 报告与低敏边界

报告沿用 OfflineEvaluationReportWriter schema version 1、runner version v1 和
target/evaluation/*.json 目录约定：

| 项目 | 固定值 |
| --- | --- |
| suite | lifegraph-importance-v1 |
| source fixture | src/test/resources/evaluation/lifegraph-promotion-v1-fixtures.json |
| report | target/evaluation/lifegraph-importance-v1-report.json |
| source scenario | EVAL-MEM-003-B |
| inputVersion | fixture-v1 |
| expectedVersion | importance-lexicographic-v1 |

versions.prompt 必须正向断言恰好等于：

~~~~json
{"key":"fixture","version":"fixture-v1","locale":"zh-CN"}
~~~~

敏感词自检从报告文本中去掉 prompt 这个字段名本身，避免把必需的
versions.prompt 误报；仍扫描用户正文、fixture 证据 token、工具参数/结果和密钥类
内容。报告 actualSummary 只允许计数、固定状态或断言通过数，例如：

- promotionH2BoundaryPassCount；
- matchableCandidateImportancePassCount；
- matchProfileImportancePassCount；
- midMemoryDecayControlPassCount；
- assertionCount 对应的 writer 汇总。

失败只转为固定 violation code，例如 FIXTURE_INVALID、REPLAY_EXECUTION、
PROMOTION_H2_BOUNDARY、MATCHABLE_IMPORTANCE_ORDER、PROFILE_IMPORTANCE_ORDER、
MID_MEMORY_DECAY_CONTROL、REPORT_LOW_SENSITIVITY；不写异常正文、堆栈或实体内容。

## 7. 测试策略与验收标准

### 7.1 单元边界

- 同 importance、不同 mentionCount：确认 mentionCount 高者在前；
- 同 mentionCount、不同 importance：确认 importance 高者在前；
- 同两项分值：确认 updatedAt 和 id 兜底稳定；
- 保留 typePriority 和 User 过滤；
- 两条中期记忆使用不同创建时间，确认 calculateDecayedImportance 的现有顺序仍成立。

### 7.2 真实 H2 闭环

- promotion 场景 B 仍使用真实 LifeGraphBuildServiceImpl 和真实 JPA/H2；
- H2 推导的 confirmed person 集合非空且包含 fixture-person-b；
- findMatchableTopByUserId 的第一页首先返回高 importance 对照实体；
- MatchProfile 持久化成功，lifeGraphSummary 的顺序体现 importance 优先；
- midMemorySummary 仍体现衰减排序；
- report 的所有 case 为 PASS，且 versions.prompt 正向匹配固定对象；
- 默认 .\mvnw.cmd -q test 全量通过，报告生成在 target/evaluation，不启动服务。

### 7.3 人物字段改造的后续决策

本切片只消费当前已有 LifeGraphEntity.importance。验收时记录回放结果能否证明字段的
来源、稳定性和匹配价值；只有当消费闭环通过后，才另行评估人物字段是否需要独立切片。
本设计不提前扩展 Person 字段、不改变人物关系模型，也不把该评估结论伪装成字段改造
已经完成。
