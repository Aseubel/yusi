# LLM Gateway Token 预算准入补强记录

日期：2026-08-07

## 背景

文章强调 Gateway 不能只按请求数或模型健康状态路由，还要在 Provider 调用前判断输入上下文、输出预算和模型上下文窗口。原 v2 路由已经保存了这些配置字段，但普通 Chat 调用没有把实际消息带入路由，只有管理端预览可以提供估算值。

## 改造内容

- 新增 provider-independent 的 `ModelTokenEstimator`，保守估算文本消息、中文/日文/韩文字符、工具调用参数和图片占位成本。
- `ModelProxyFactory` 在 `plan` 前从 `ChatRequest` 提取输入估算和请求输出上限，路由决定后再把 route 预算应用到实际请求。
- `ModelRouterService` 按 `max-input-tokens` 和 `context-window-tokens` 过滤候选；主 tier 超出窗口时只保留预算可容纳的 fallback tier。
- route 未配置输出上限时按 1024 token 预留；用户请求的更小输出上限不会被 route 放大。
- Chat 流程把业务 `requestId`、`runId` 和 `userId` 放入路由上下文，使 attempt trace 可以回放到业务请求。

## 边界

估算用于调用前准入，不声称等同于任何供应商 tokenizer，也不替代供应商返回的真实 usage。当前阶段把预算准入和 attempt 级 reserve/reconcile 接入 Redis：用户、模型、Provider 都可以配置请求数和 Token 固定窗口。Redis Lua 脚本原子检查所有桶，真实 usage 到达后幂等调整 Token 预留；usage 缺失或断流按保守预留挂账，预留状态通过 TTL 自动兜底。成本统计仍以真实 usage 和价格快照为准。

## 配置与边界

- 配置入口为 `model.gateway.admission`，请求数和 Token 上限均为固定窗口值，`0` 表示关闭对应维度。
- 当前产品是单用户模型，使用 `userId` 做调用归因；组织/租户配额属于后续明确引入协作或计费边界时的独立领域变更，不在本次 Gateway 准入范围内。
- Provider 调用已经开始后不自动释放未知 usage。只有确定没有调用 Provider 时才应使用 `ModelBudgetAdmission.release`。
- 这仍然不是余额扣费系统：它控制窗口内并发放量和 Token 压力，价格结算继续由真实 usage、价格版本和调用 trace 负责。

## 验证

- `./mvnw -Dtest=ModelBudgetAdmissionTest,ModelTokenEstimatorTest,ModelRouterServiceTest,ModelProxyFactoryTest test`
- `git diff --check`
