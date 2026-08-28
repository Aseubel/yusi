# Benchmark 落地与注销闭环、思考模式场景化记录

日期：2026-08-28

## 本轮工作

### 1. 三层基准首个基线（runId 50caa324 → 多轮）

- 检索层 recall@k/MRR/nDCG（MILVUS MID_MEMORY 15 查询 + DIARY 8 查询，MRR 全 1.0）。
- 语义层 LLM-as-judge（chat/match/extraction）。
- E2E 旅程（注册→登录→日记→记忆→聊天→注销收尾，bench- 前缀用户 + BenchmarkDataGuard 清理）。
- 基线：retrieval 0.9877 / match 0.9553~0.997 / chat 0.8611~0.9167 / e2e 0.8 / extraction 0.38→0.46 / overall ≈0.82。
- extraction runner 增加 per-case 预测明细（predictedEntities/predictedRelations 落盘 part file），后续离线 diff 无需重跑 LLM。
- 离线分析结论：extraction precision 损失主要来自抽取边界过宽（一次性场所/物品/背景人物全进图）与 gold 漏标（已补 chen case「小何」）；su case LIKES 关系全 miss 待明细定位。

### 2. 注销闭环修复（E2E 收尾 50002 的两个根因）

- OSS NoSuchBucket：本地占位 bucket 导致 `listObjectsV2` 404，注销 pendingRetry。修复：bucket 不存在 = 无对象可删，清理语义视为完成（`OssService` 解开 OperationException cause 链判断错误码）。
- 异步摄取竞态：日记触发的认知摄取在删除事务运行期间重写 match_profile，requireClean 拦截为 `database_invariant`。修复：`InterfaceUsageMonitor.isUserSuppressed` + `AgentCognitionOrchestratorImpl.ingest` 入口对注销中用户跳过摄取。
- 单测锁定：`AccountDeletionRaceGuardTest` 用 H2 触发器在删除事务尾部确定性注入并发写入，验证 fail-closed → 重试收敛；`AgentCognitionOrchestratorTest` 锁定抑制期跳过。

### 3. 测试基建修复（此前已损坏、非本轮功能引入）

- Milvus mock 未打桩 `getLoadState` 导致三个测试类各空转 60s 超时。
- `MilvusCollectionProperties` 注册点挂在 `@Profile("!test")` 的 MilvusConfig 上，46 个 @SpringBootTest 上下文起不来；新增无 profile 的 `MilvusCollectionConfig` 作为唯一注册点。
- `ModelRoutingProperties.isThinkingDisabled()` 派生 getter 被 Jackson 序列化，`cloneConfig` 往返失败；加 `@JsonIgnore`。
- 全量 564 测试通过。

### 4. 思考模式 tier 化（场景级可配置）

- `ModelTierDefinition.thinkingEnabled`（tier 级覆盖）> `ModelDefinition.thinkingEnabled`（模型级）> null（服务端默认）。
- 解析：`ModelRoutePlanner` 取 primary-tier 的 tier 覆盖写入 `ModelRouteParameters.thinkingEnabled`。
- 生效：模型代理调用窗口内通过 `ThinkingRequestContext`（ThreadLocal）传递给 DashScope HTTP 装饰器；client bundle 保持模型级缓存不变。
- DashScope 装饰器注入语义从「套上即强制 false」改为三态显式注入，消除「仅配 max-output-tokens 也会顺带关思考」的隐式副作用。
- DeepSeek 严格 API 由外层 FilteringHttpClient 剥离 `enable_thinking`/`max_tokens`（此前装饰器 patch 发生在 sanitize 之后，会污染 DeepSeek 请求）。
- prod 示范：`tiers.graph.thinking-enabled: false`（结构化抽取无需推理链）。

### 5. prod 配置兜底

- qwen：`max-output-tokens: ${QWEN_MAX_OUTPUT_TOKENS:8192}`、`thinking-enabled: ${QWEN_THINKING_ENABLED:false}`（对齐 dev 实测有效配置）。
- deepseek：`max-output-tokens: ${DEEPSEEK_MAX_OUTPUT_TOKENS:8192}`；不配模型级 thinking（严格 API，按需由 tier 覆盖注入并经剥离保护）。
- 长文本场景：`soul-match-letter`/`soul-weekly-report` 补 `max-output-tokens` 4096 兜底（此前落回默认 1024 会截断信件/周报）。

## 相关 bugs

- [异步摄取与注销事务竞态](bugs/2026-08-28-account-deletion-cognition-race.md)
- [OSS SDK 异常包装导致注销失败归类错误](bugs/2026-08-28-oss-serviceexception-wrapper.md)
- [DashScope 装饰器污染 DeepSeek 严格 API 请求](bugs/2026-08-28-dashscope-pollutes-deepseek.md)
- [langchain4j RESPONSES 协议不支持 customParameters](bugs/2026-08-28-langchain4j-responses-custom-parameters.md)
- [Jackson 派生 getter 破坏配置往返](bugs/2026-08-28-jackson-derived-getter-roundtrip.md)
- [H2 触发器内隐式提交被禁](bugs/2026-08-28-h2-trigger-implicit-commit.md)
- [Profile 化配置类导致 test 上下文缺 bean](bugs/2026-08-28-profile-gated-properties-registration.md)
