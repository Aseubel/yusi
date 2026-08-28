# DashScope 装饰器污染 DeepSeek 严格 API 请求

日期：2026-08-28

## 现象

代码注释与直觉均认为「DeepSeek 的严格 API 不经过 DashScopeThinkingHttpClientBuilder 装饰器」，
但梳理 client 装饰链后发现 DeepSeek Responses 请求体会被注入 `enable_thinking` 与
`max_tokens`——DeepSeek 严格校验未知字段时将直接 400。prod 引入 deepseek 作为 tier 成员后
必然触发。

## 根因

`OpenAiCompatibleChatModelProvider.httpClientBuilder` 的原装饰顺序：

```
DeepSeekResponsesHttpClientBuilder(DashScopeThinkingHttpClientBuilder(inner))
```

请求流为 `FilteringHttpClient.sanitize`（剥字段，先执行）→
`ThinkingOffHttpClient.patchBody`（DashScope 注入，后执行）。DashScope patch 发生在
DeepSeek sanitize **之后**，sanitize 剥不掉装饰器后来加上的字段。注释描述的
「DeepSeek 不经过此装饰器」从未成立——这是装饰器嵌套顺序与「谁后执行谁说了算」的时序误判。

## 修复

调换嵌套顺序，让 DeepSeek 过滤器成为**外层**（sanitize 后执行）：

```
DashScopeThinkingHttpClientBuilder(DeepSeekResponsesHttpClientBuilder(inner))
```

请求流变为 patch（注入 DashScope 字段）→ sanitize（剥掉 `enable_thinking`/`max_tokens`）→
原始 client。同时：

- `DeepSeekResponsesHttpClientBuilder.UNSUPPORTED_FIELDS` 增加 `enable_thinking`、`max_tokens`。
- DashScope 装饰器注入语义改为三态显式（tier 覆盖 > 模型级配置 > 不注入），
  消除「仅配置 max-output-tokens 也顺带注入 enable_thinking=false」的隐式副作用。

## 验证

- 模型路由相关单测 25 个通过。
- 待 prod 首次 deepseek 调用后以 `model_call_trace` 归一化失败类别复核（无 4xx 参数错误）。
