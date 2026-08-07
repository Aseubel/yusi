# Gateway SSE provider error 修复记录

日期：2026-08-07

## 现象

OpenAI-compatible provider 拒绝思考消息后，LangChain4j 在异步流回调中触发 `ModelInvocationException`。旧逻辑调用 `SseEmitter.completeWithError`，Spring 随后使用普通 JSON 全局异常处理器处理已经声明为 `text/event-stream` 的响应，产生二次异常：

```text
No converter for [class com.aseubel.yusi.common.Response] with preset Content-Type 'text/event-stream'
```

## 原因

异步 SSE 已经开始处理后，异常不能再通过普通 `@RestControllerAdvice` 返回 JSON。与此同时，只有 `thinking` 的 `AiMessage` 被序列化时没有 `content`，也没有 `tool_calls`，不符合 OpenAI-compatible 请求格式。

## 修复

- ModelProxyFactory 在发送请求前将没有文本且没有工具调用的 assistant 消息规范化为 `content: ""`，并保留 thinking、工具调用、attributes 以及响应 metadata/usage。
- AiController 的 provider 异步错误先发送 `run.failed`，再正常完成 emitter 和清理会话，不再从异步回调调用 `completeWithError`。
- GlobalExceptionHandler 对已提交或 `text/event-stream` 响应不再写入普通 JSON body，避免 converter 二次异常。
- 增加 AiController 回归测试，验证 provider error 会发送 `run.failed`、释放锁并从流注册表移除会话。

## 结果

客户端可以收到明确的 `run.failed` 事件并结束 SSE；原始 provider 错误不再触发第二个 JSON converter 异常。真实供应商请求仍需在目标环境使用有效 API key 做联调验证。
