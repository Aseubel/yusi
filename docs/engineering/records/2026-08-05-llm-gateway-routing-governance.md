# LLM Gateway 路由治理发布记录

日期：2026-08-05

## 目标

将模型治理收敛为 schema v2 应用内可解释网关，并把管理员主流程迁移到可视化模型注册表、tier 路由矩阵、策略编辑器、候选链预览和运行状态面板。

## 发布边界

- 第一版只启用固定规则、逻辑 tier、健康过滤、错误分类 fallback、usage/成本元数据和审计。
- 场景规则只引用内部 tier ID；真实模型名只存在于物理模型注册表。
- `CHAT_COMPLETIONS`、`RESPONSES` 和 `ANTHROPIC_MESSAGES` 由 Provider Adapter 按 provider/protocol 组合统一创建同步与流式客户端；ASR 继续使用独立能力边界。
- 控制台保存完整 schema v2 快照；JSON 导出只表示当前 v2 快照，不提供旧格式转换。
- 调用轨迹不保存 prompt、回答、思维链、工具参数或工具结果。

## 配置发布与回滚

### 正常发布

1. 管理员从 `GET /api/model/console` 取得版本化快照。
2. 前端通过可视化控件生成 `PUT /api/model/console` 请求，并携带 `expectedVersion`。
3. 服务端读取 MySQL active 快照并拒绝过期版本。
4. 服务端校验模型、tier、route 引用，合并未修改的服务端密钥，保存下一版本快照和脱敏审计。
5. Redis runtime bucket 写入成功后发布配置事件，最后替换本地配置引用。

### 回滚

回滚不是直接编辑 JSON。操作员应恢复目标 MySQL 快照版本，重新发布对应 Redis 配置，并用路由预览验证：

- `zh/chat`
- `zh/situation-analysis`
- `zh/memory-extract`
- `zh/emotion-analysis`
- `zh/soul-match`
- ASR 路由

验证过程中应确认 API key 只显示“已配置/未配置”，候选链包含不可用模型的排除原因，且已产生流式输出的请求不会切换模型。

## 数据与指标

`model_runtime_config` 保存 active 全量配置和版本；`model_config_change_log` 记录 `UPDATE_CONFIG`、回滚和失败原因；`model_call_trace` 保存低敏调用尝试。成本未知时显示 `unknown cost`，不得用默认价格伪造精确成本。

## 发布顺序

1. 部署包含 v2 schema 校验、三种协议 adapter 和版本化发布的后端。
2. 部署可视化前端，确认未保存草稿、版本冲突和密钥状态可见。
3. 发布 v2 配置；保留上一份 Redis 配置和 MySQL 版本供回滚。

## 阶段门

先用固定规则获得成本、质量、延迟和 fallback 证据；在有足够轨迹后建立评测集和轨迹回放，再评估语义路由或学习型路由。第一版不引入分类器、embedding 相似路由、A/B 实验、租户配额、语义缓存或独立网关部署。

## 验证证据

- 环境：Java 21、Spring Boot 3.4.5、Node.js/pnpm；focused suite 未使用真实 Provider API key。
- 后端 focused Maven suite：`./mvnw -Dtest=ModelConfigCenterTest,ChatModelProviderRegistryTest,ModelRoutePolicyMatcherTest,ModelRouterServiceTest,ModelInvocationErrorClassifierTest,ModelProxyFactoryTest,ModelUsageExtractorTest,ModelManagementControllerTest,AiControllerCancellationTest test`，33 tests passed。
- 后端编译：`./mvnw -DskipTests compile`，`BUILD SUCCESS`。
- 前端 Vitest suite：`pnpm --dir frontend test --run`，4 个测试文件、14 tests passed。
- 前端 ESLint：`pnpm --dir frontend lint`，exit 0。
- 前端 production build：`pnpm --dir frontend build`，TypeScript/Vite/PWA build succeeded。
- 配置与 migration 静态检查：`git diff --check` 无输出；dev/prod 均含 v2 `schema-version/default-route/tiers/routes`；敏感字段扫描未发现原始密钥；前端主流程未使用旧 JSON editor。
- 回归测试覆盖：数据库 active 快照版本冲突、Redis 发布失败保留旧本地版本并追加失败审计、全禁用 Chat 模型禁止成为主 tier、密钥掩码合并、显示名称保存。
- 手动路由矩阵：已启动 `http://localhost:5174`，但 `/admin/models` 被认证守卫重定向到登录页，当前没有管理员会话，因此 `zh/chat` 预览、fallback 排除原因、冲突保存和流式交互尚未完成真实浏览器验证；该项需要在管理员登录环境执行。
