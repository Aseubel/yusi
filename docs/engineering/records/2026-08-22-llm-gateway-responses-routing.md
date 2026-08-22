# LLM Gateway Responses 协议与 tier 路由记录

日期：2026-08-22

## 请求边界

聊天请求的调用链为：

```text
AiController
  -> Assistant / LangChain4j AiServices
  -> ModelProxyFactory
  -> ModelRouterService
  -> ModelInstanceRegistry
  -> provider adapter
  -> LangChain4j HTTP client
```

模型协议由生效的模型注册表决定，不由请求 scene 猜测。`RESPONSES` 使用
`OpenAiResponsesChatModel` 和 `OpenAiResponsesStreamingChatModel`，endpoint 为 base URL 加
`/responses`；`CHAT_COMPLETIONS` 使用对应的 `/chat/completions` client。GLM/智谱别名仍走
OpenAI-compatible adapter，但 GLM-4.6V-Flash 应配置为 `CHAT_COMPLETIONS`，base URL 使用
`https://open.bigmodel.cn/api/paas/v4`，由 adapter 补 `/chat/completions`。

生效配置顺序固定为：MySQL active > Redis canonical > YAML。每次启动和配置事件都会记录来源、
版本、模型数、tier 数和 route 数；每个模型实例还记录 protocol、脱敏 endpoint、同步/流式
client 类型、能力、priority 和 weight。

## Scene 与能力

- 无图片的聊天保持 `chat` scene。
- 图片请求统一使用 `image-understanding` scene。
- `image-understanding` 只接受声明 `VLM` 的模型；空 capability 列表只表示默认文本能力，
  不隐含视觉能力。
- Responses 输入会丢弃历史 thinking/encrypted reasoning，但保留文本、图片、工具调用和工具结果。

## Tier 策略

每个 tier 独立执行自身 strategy，先做能力、scene、token window 和健康过滤，再生成候选链：

- `FAIL_OVER`：健康候选优先，按 priority、model ID 稳定排序；失败且未产生输出时才进入下一个候选。
- `ROUND_ROBIN`：以 tier ID 为 key 独立轮询健康候选，不同 tier 不共享游标。
- `LEAST_LATENCY`：健康候选中无有效延迟样本的模型先获得探索请求，之后按指数平均延迟升序；延迟相同按 model ID。这样不会因首个成功模型较快而永久饥饿其他成员。
- `WEIGHTED_RANDOM`：只从健康且正权重模型中按权重生成随机排列；零权重模型永远不会被选中。
- `DOWN` 模型在冷却期内排除；`nextProbeAt` 到期后进入候选尾部，由 `allowRequest` 只放行一个半开探测。

ASR 也按 enabled tier 的 strategy、能力和健康状态选择，但只注册支持
`STREAMING_SPEECH_TO_TEXT` 的实时客户端；会话成功或失败都按完整会话耗时更新延迟样本。

配置发布前拒绝非正 weight、负 priority、非正 timeout/context window、重复成员、能力不匹配、
不存在或禁用的 tier，以及没有满足 scene 能力的 primary/fallback tier。

## 验证边界

已执行现有 focused suite（54 tests）和 Maven 编译，未新增测试、未使用真实 API key。真实
provider 请求、MySQL/Redis 生效快照和 Docker 日志必须在部署后按 bug 记录中的启动日志确认；
源码验证不能替代该联调证据。
