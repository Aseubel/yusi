# GLM-4.6V-Flash API 基础

核对日期：2026-08-23

官方来源：[GLM-4.6V-Flash](https://docs.bigmodel.cn/cn/guide/models/free/glm-4.6v-flash#curl)

## 调用协议

官方 cURL 示例使用 Chat Completions：

```text
POST https://open.bigmodel.cn/api/paas/v4/chat/completions
```

项目配置应使用：

```yaml
provider: glm
protocol: CHAT_COMPLETIONS
baseurl: https://open.bigmodel.cn/api/paas/v4
model: glm-4.6v-flash
capabilities: [CHAT, STREAMING_CHAT, VLM]
```

项目 adapter 会补 `/chat/completions`。GLM-4.6V-Flash 的这份官方模型文档没有提供
`/responses` 调用示例，因此不能把它配置为 `RESPONSES`；`RESPONSES` 与
`CHAT_COMPLETIONS` 是两条不同的请求链路。

## 图片请求

图片使用 OpenAI-compatible message content 数组，典型结构是：

```json
{
  "role": "user",
  "content": [
    {"type": "text", "text": "请描述这张图片"},
    {"type": "image_url", "image_url": {"url": "https://example.com/image.jpg"}}
  ]
}
```

在项目中，带图片的请求必须由模型声明 `VLM` 能力并进入 `image-understanding` scene；纯文本
请求保持 `chat` scene。不要把 Responses 的 `input_image` 块直接发送到 GLM 的 Chat
Completions endpoint。

## 项目排查要点

- 启动日志应出现 `protocol=CHAT_COMPLETIONS`、`OpenAiChatModel`、
  `OpenAiStreamingChatModel` 和 endpoint `.../chat/completions`。
- HTTP 400 时查看 `errorSummary`，不要只看归一化的 `INVALID_REQUEST`；详细错误只保留低敏摘要。
- `baseurl` 已经包含 `/chat/completions` 时，项目会去重该 suffix；推荐仍配置到 `/v4` 根路径。
