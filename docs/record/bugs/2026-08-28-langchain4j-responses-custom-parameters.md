# langchain4j RESPONSES 协议不支持 customParameters

日期：2026-08-28

## 现象

route 级 `customParameters`（`RoutePolicyDefinition.customParameters` → `ModelRouteParameters`
→ `OpenAiChatRequestParameters.Builder.customParameters`）在 qwen 场景下从未生效——而 prod 的
qwen 恰恰配置为 `protocol: RESPONSES`。

## 根因

`ModelProxyFactory.buildProtocolParameters` 只在 `CHAT_COMPLETIONS` 分支写入
customParameters；`RESPONSES` 分支使用的 `OpenAiResponsesChatRequestParameters.Builder`
（langchain4j 1.18.0）经 javap 反编译确认**没有 customParameters 支持**——OpenAI Responses
API 本身没有任意自定义字段的通道。因此 DashScope 兼容专属参数（`enable_thinking`）无法通过
请求参数传递，只能走 HTTP 层装饰器注入请求体。

这个约束直接决定了 tier 级 thinking 的实现方式：模型代理在调用线程上通过
`ThinkingRequestContext`（ThreadLocal）传递 tier 覆盖值，由 DashScope HTTP 装饰器在发送
请求体时读取注入，而不是构建请求参数。

## 修复

- 无 API 层修复空间（SDK 限制）；以 `ThinkingRequestContext` + DashScope 装饰器实现
  请求级参数注入，client bundle 保持模型级缓存。
- ThreadLocal 方案的边界：覆盖值仅需在「设置 → HTTP 请求发送 → 清除」窗口内可见，
  langchain4j 同步/流式调用的请求体发送都发生在调用线程，流式响应回调线程不再发送请求体，
  因此无跨线程污染。

## 验证

- `ModelProxyFactoryTest` 10 个用例通过（含三协议参数适配）。
- 联网/反编译结论沉淀：`OpenAiResponsesChatRequestParameters.Builder` 方法清单已核对
  （1.18.0，无 customParameters）。
