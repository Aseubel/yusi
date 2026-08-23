# 聊天图片路由场景误分流

日期：2026-08-24

## 根因

聊天流在包含图片时把模型路由上下文设置为 `image-understanding`，模型代理又会
按图片请求自动把 `chat` 场景改写为 `image-understanding`。这使完整 Agent 聊天
请求进入视觉专用 route，而不是设计中的多模态聊天 route。

## 修复

- 聊天流始终使用 `chat` 场景；图片仍作为多模态消息内容传递。
- 模型代理尊重显式场景，不再按请求是否含图片自动改写 route。
- 日记认知流程继续显式使用 `image-understanding` 场景。

## 验证

回归测试锁定带图聊天的 route scene 为 `chat`，并保留日记图片理解服务的
`image-understanding` 场景测试。
