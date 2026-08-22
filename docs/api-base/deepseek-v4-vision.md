# DeepSeek V4 Vision API 基础

核对日期：2026-08-23

官方来源：

- [图像理解](https://api-docs.deepseek.com/zh-cn/guides/vision)
- [Responses API](https://api-docs.deepseek.com/zh-cn/guides/responses-api)
- [创建 Response](https://api-docs.deepseek.com/zh-cn/api/create-response)

## 模型与 endpoint

视觉模型 ID 是 `deepseek-v4-flash-vision-exp`，base URL 为
`https://api.deepseek.com`。OpenAI-compatible 的两个请求路径分别是：

| 协议 | 请求路径 | 项目配置 |
| --- | --- | --- |
| Chat Completions | `/chat/completions` | `protocol: CHAT_COMPLETIONS` |
| Responses | `/responses` | `protocol: RESPONSES` |

项目的 provider adapter 会根据 protocol 选择 LangChain4j client，并把 base URL 与对应路径拼接。
因此 `baseurl` 应配置到 API 根路径，不要把 `/responses` 或 `/chat/completions` 再写进配置。

Responses 请求由 `input` 承载消息。图片内容块使用 `input_image`，图片 URL 形式是：

```json
{
  "type": "input_image",
  "image_url": "https://example.com/image.jpg",
  "detail": "auto"
}
```

## 图片约束

- 视觉模型支持 JPEG、PNG、GIF、WebP。
- 图片只能出现在 user/developer 消息，或 function/custom tool output 中；system/assistant
  消息携带图片会返回 HTTP 400。
- Responses 的图片可以使用 base64 data URL 或外部 HTTPS URL；项目聊天链路使用 OSS 生成的
  外部 URL。
- 官方限制包括 48 MiB 请求体、单张图片 32 MiB（非 Files API）和单请求最多 600 张图片；
  项目入口另有最多 3 张图片的业务限制。

## 项目兼容约定

- Responses 历史 assistant 消息不带入 `thinking` 和 `encrypted_reasoning`，避免把上一次响应的
  推理内部字段当作新的输入。
- DeepSeek Responses 适配层会移除 LangChain4j/OpenAI client 生成的兼容性可选字段：
  `store`、`previous_response_id`、`include`、`truncation`、`service_tier`、
  `safety_identifier`、`prompt_cache_key`、`prompt_cache_retention`、`stream_options`。
- 供应商返回的 HTTP 状态、错误 JSON 中的 `code/type/param/message/reason` 会进入受限错误摘要；
  不记录请求正文、响应正文或 API key。
- Responses 流式 reasoning 只用于后端阶段状态，不直接作为用户回答正文发送。

## 400 排查顺序

1. 查看启动日志中的 `protocol`、实际 client 类型和脱敏后的 endpoint，确认不是误走
   `/chat/completions`。
2. 查看调用日志中的 `modelId`、`messageCount`、`imageCount`、`errorSummary`，区分模型、协议、
   图片位置和字段兼容问题。
3. 确认生效配置来源。项目优先使用 MySQL active，其次 Redis canonical，最后才是 YAML/env；
   只改 YAML 不会覆盖旧的运行时快照。
4. 若仍为 400，保留 provider 返回的低敏摘要，并在部署环境用同一 model/base URL 做最小文本请求
   和最小 user 图片请求验证；源码验证不能替代真实供应商联调。
