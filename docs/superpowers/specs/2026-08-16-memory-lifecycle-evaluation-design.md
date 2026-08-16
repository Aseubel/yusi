# 中期记忆生命周期与消费边界回放设计

## 状态

- 状态：已确认，待实施
- 日期：2026-08-16
- 所属阶段：Phase 4 第二刀
- 相关路线图：[Yusi Agent 产品与工程演进计划](../../engineering/plans/2026-08-04-yusi-agent-product-roadmap.md)
- 前置切片：[LifeGraph / Timeline 离线回放基线](2026-08-16-offline-evaluation-replay-baseline-design.md)

## 目标

在不启动应用服务、不连接真实 Milvus、不访问远程模型和不使用真实用户数据的前提下，
用真实 H2 repository、记忆生命周期服务和匹配画像服务回放 `EVAL-MEM-001`，验证中期
记忆的状态边界不会被后续消费方绕过。

本切片关注的是“记忆是否仍可被使用”，不是“记忆中心是否仍能展示它”。隐藏、过期和
已合并记忆可以继续出现在记忆中心，供用户理解和恢复；但它们不能进入 Agent 可用上下文、
向量检索结果或匹配画像。删除后的数据库记录和向量残留也不能继续产生可用结果。

## 范围

### 包含

1. 复用 Phase 4 的脱敏 fixture 红线和版本化报告封装，新增 `memory-lifecycle-v1` 套件。
2. 使用真实 H2 验证 `MidTermMemoryRepository` 的 available/matchable 查询。
3. 使用真实 `MidTermMemorySearchService` 验证向量结果经过数据库状态和用户归属二次过滤。
4. 使用真实 `MatchProfileAssemblerImpl` 验证匹配画像的 `midMemorySummary` 只消费可匹配记忆。
5. 覆盖隐藏、过期、已合并、未开放匹配、删除和向量删除失败后的残留路径。
6. 输出 `target/evaluation/memory-lifecycle-v1-report.json`，并由默认 Maven 测试和 CI 归档门槛接收。

### 不包含

- 不新增生产数据库表、migration、API、页面或业务状态字段。
- 不改变记忆中心对隐藏/过期记忆的展示语义。
- 不连接真实 Milvus、Embedding 服务或远程模型。
- 不把 Milvus 服务端 `expr` 过滤当成本切片已验证的结论。
- 不评测 Prompt 自然语言质量、模型幻觉、回答语气或匹配排序质量。
- 不把本切片扩展为 LifeGraph、对话和主动性评测的统一执行器重构。

## 已确认约束

### 1. 每个场景必须先有正向检索对照

每个 `EVAL-MEM-001-*` 场景都必须先证明至少一个当前可用记忆确实能被检索，再断言
受限记忆缺席。受限记忆的“未出现”不能单独构成通过条件，避免 Milvus mock 没有返回
任何结果时出现空洞的无泄漏通过。

断言采用以下逻辑，正向对照失败时相应的缺席断言也失败：

```java
boolean positive = retrievedSummaries.contains(expectedAvailableSummary);
checks.check("RETRIEVAL_POSITIVE_CONTROL", positive);
checks.check("RESTRICTED_MEMORY_ABSENT",
        positive && restrictedSummaries.stream().noneMatch(retrievedSummaries::contains));
```

场景 B 除向量检索正向对照外，还必须证明允许匹配的摘要确实进入 `MatchProfile`，然后
才能把受限摘要不在画像中作为有效断言。场景 C 删除后必须证明保留的本用户记忆仍可用，
以及另一个用户的记忆在另一个用户查询下仍可用。

### 2. EVAL-MEM-001-C 覆盖向量删除失败

场景 C 使用真实 `MidTermMemoryLifecycleService.delete`，将 `MidTermMemoryVectorService.delete`
mock 为抛出异常。该异常必须沿真实实现被吞掉，数据库删除仍然完成；随后让
`MilvusClientV2` mock 返回包含已删除记忆的旧向量候选，真实 `MidTermMemorySearchService`
必须因为 `findByIdAndUserId` 查不到该行而过滤掉它。

因此该场景同时验证：

- 向量删除失败不会阻止数据库删除；
- 删除后的旧向量候选不会重新成为可用记忆；
- 删除失败日志或异常正文不进入报告；
- `profileLeakCount` 仍为 0。

### 3. 场景 B 的断言层级和 mock 面

场景 B 以 `MatchProfile` 产物作为匹配消费的验收层级，不以 Mockito 对
`findMatchableByUserId` 的调用次数作为通过条件。测试使用真实 H2 的
`MidTermMemoryRepository` 和真实 `MatchProfileAssemblerImpl`，调用
`MatchProfileAssembler.refreshProfile(userId)`，断言返回的 `MatchProfile.midMemorySummary`：

- 包含允许匹配的正向摘要；
- 不包含 `matchAllowed=false`、hidden、expired 或 merged 摘要；
- `profileLeakCount == 0`。

该层级下不 mock `MidTermMemoryRepository`、`MatchProfileRepository`、`UserService` 或
`UserPersonaService`；测试创建虚构用户并使用真实 H2 查询。只 mock 外部副作用边界：
`EmbeddingModel` 返回固定一维向量，`MilvusClientV2` 不访问网络且接受画像同步调用。

### 4. 跨用户隔离拆成两个独立断言

场景 C 不把“跨用户隔离”合并成一个模糊检查，而是分别输出：

- `OTHER_USER_RESIDUAL_NOT_LEAKED`：用户 A 的检索结果不能包含用户 B 的记忆，即使向量
  mock 返回了用户 B 的候选；
- `DELETE_DOES_NOT_AFFECT_OTHER_USER`：删除用户 A 的记忆后，用户 B 查询仍能得到自己的
  正向记忆。

这两个断言验证的是应用层 `findByIdAndUserId` 所在的二次过滤和 H2 用户边界；本切片
明确不声称覆盖 Milvus 服务端 `HybridSearchReq.filter` / `expr` 的执行正确性。报告和设计
文档不把该层写成已覆盖能力，后续如需验证必须增加真实 Milvus 或专门的协议级测试。

### 5. profileLeakCount 的固定门槛

`profileLeakCount` 定义为受限记忆的合成摘要 token 出现在 `MatchProfile.midMemorySummary`
中的数量。每个 case 和整套回放的验收阈值固定为 `0`，不是相对基线，也不因样本量调整。

## 方案与数据流

采用独立的 H2 回放套件，同时抽取通用的低敏报告 envelope：

```text
脱敏 memory fixture
  -> 共享红线校验与 typed loader
  -> H2 写入虚构用户和 MidTermMemory
  -> 真实 repository / lifecycle / search / match-profile service
  -> 外部向量、Embedding、删除失败 mock
  -> 正向对照断言
  -> 受限记忆缺席、删除残留、跨用户隔离断言
  -> memory-lifecycle-v1 JSON report
  -> JUnit 默认门槛 + CI artifact
```

现有 `lifegraph-timeline-v1` 报告继续保持相同 JSON 顶层字段和输出路径。通用 writer 只
负责 schema version、suite、runner、版本槽位、case 结果和聚合状态；LifeGraph 和记忆
套件各自维护领域 summary，避免把不同领域的计数硬塞进同一个固定 DTO。

## 场景定义

### EVAL-MEM-001-A：可用上下文与生命周期过滤

为同一个虚构用户写入至少一条 active 记忆、一条 `matchAllowed=false` 但仍可用于普通
回顾的记忆，以及 hidden、expired、merged 记忆。向量 mock 返回所有候选。

执行顺序：

1. 先断言 active 和 chat-only 记忆至少有一个能被 `searchMidTermMemory` 返回。
2. 再断言 hidden、expired、merged 不出现在向量检索结果。
3. 调用 `getRecentMemories`，断言可用记忆仍存在，受限记忆不出现。
4. 记忆中心展示状态不作为泄漏断言，避免把透明度页面和 Agent 消费边界混淆。

### EVAL-MEM-001-B：匹配画像授权边界

为同一个虚构用户写入允许匹配的 active 记忆、未允许匹配的 active 记忆，以及 hidden、
expired、merged 记忆。向量检索仍先执行正向对照，确认可用摘要可返回。

然后调用真实 `MatchProfileAssembler.refreshProfile`，先断言允许匹配的摘要进入
`midMemorySummary`，再断言其他摘要全部缺席，并固定断言 `profileLeakCount == 0`。

匹配画像同步到 Milvus 的写入只使用 mock 的 Embedding 和 Milvus 客户端；本切片验收的是
数据库生成的 `MatchProfile` 产物，不验收 Milvus 服务端画像索引。

### EVAL-MEM-001-C：删除、向量失败和跨用户隔离

准备用户 A 的待删除记忆、用户 A 的保留记忆和用户 B 的保留记忆。删除前先分别验证用户
A 的待删除记忆和保留记忆存在正向检索结果。随后让向量删除抛异常并删除用户 A 的待删除
记忆。

删除后执行：

1. 断言用户 A 的保留记忆仍可检索，作为删除后的正向对照；
2. 断言用户 A 的待删除记忆不再可检索，覆盖向量残留；
3. 断言用户 A 的结果不包含用户 B 的候选（`OTHER_USER_RESIDUAL_NOT_LEAKED`）；
4. 以用户 B 查询，断言用户 B 的记忆仍可检索（`DELETE_DOES_NOT_AFFECT_OTHER_USER`）；
5. 断言数据库中用户 A 的目标行消失，且 delete 调用了向量清理；
6. 断言画像泄漏计数为 0。

## 脱敏 fixture

新增 `/evaluation/memory-lifecycle-v1-fixtures.json`，只允许以下内容：

- `fixture-user-*`、`fixture-memory-*` 等虚构 ID；
- `memory-summary-*` 合成摘要 token；
- `ACTIVE`、`HIDDEN`、`EXPIRED`、`MERGED` 生命周期枚举；
- `matchAllowed`、`mergedIntoKey`、场景期望集合和布尔开关；
- 向量候选的合成 memory key 和 owner key。

fixture 不提供正文、Prompt、工具参数、工具结果、密钥、密码或可还原个人信息字段。共享
红线校验继续拒绝 `plainContent`、`rawText`、`prompt`、`toolArguments`、`toolResult`、
`secret`、`password` 和 `content`，同时校验字段长度、ID 前缀和摘要 token 前缀。

## 报告与 CI

报告路径：`target/evaluation/memory-lifecycle-v1-report.json`。

报告沿用 schema version `1` 和四类版本槽位：`model`、`prompt`、`retrieval`、`ranking`。
领域 summary 至少包含：

- `availableCount`
- `matchableCount`
- `retrievedCount`
- `profileLeakCount`
- `remainingRowCount`
- `crossUserLeakCount`

`profileLeakCount` 必须为 0；任何 case 失败、正向对照失败、删除残留、跨用户泄漏或
受限记忆出现在画像中都会使 JUnit 失败并让报告状态为 `FAIL`。

CI 已归档 `target/evaluation/*.json`，本切片将 artifact 名称从 LifeGraph 专名调整为
`offline-evaluation-reports`，使多个评测套件不会被错误命名。不得增加远程服务启动步骤。

## 验收标准

1. 三个 `EVAL-MEM-001-*` 场景每个都有先正向、后负向的检索断言。
2. 场景 C 在向量删除抛异常时仍能证明数据库删除完成且旧向量零泄漏。
3. 场景 B 明确以真实 H2 + `MatchProfile` 产物验收，mock 面只包含 Embedding/Milvus 外部副作用。
4. 场景 C 分别通过用户 B 残留不泄漏和删除用户 A 不影响用户 B 两个断言，并明确未覆盖 Milvus expr。
5. 每个 case 的 `profileLeakCount == 0`，整套报告为 `PASS`。
6. fixture 通过共享脱敏红线校验，报告不含摘要 token、异常正文或外部服务返回正文。
7. focused test 和全量 `mvn test` 均通过，且不启动应用服务、不连接远程依赖。
