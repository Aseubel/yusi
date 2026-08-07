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

估算用于调用前准入，不声称等同于任何供应商 tokenizer，也不替代供应商返回的真实 usage。当前仍不对租户余额做 Redis token reserve/reconcile；成本统计以真实 usage 和价格快照为准。

## 验证

- `./mvnw -Dtest=ModelTokenEstimatorTest,ModelRouterServiceTest,ModelProxyFactoryTest test`
- `git diff --check`
