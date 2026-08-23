# 聊天图片签名 URL 过期

日期：2026-08-23

## 根因

OSS 图片访问 URL 默认有效期为 3600 秒。聊天上传接口同时返回 object key 和签名 URL，但聊天内存的数据库内容、`images` 字段、Redis 缓存和前端消息状态都曾直接复用签名 URL。URL 过期后，历史消息无法显示，重新调用模型时也会把失效 URL 传给视觉模型。日记读取流程每次按 object key 重新生成 URL，因此未出现同样的问题。

## 修复

- 新聊天消息只在 `chat_memory_message.images` 保存用户拥有的 OSS object key，数据库 `content` 不再保存图片签名 URL。
- Redis 命中、数据库读取、历史接口和旧数据读取都会按 object key 重新生成短期 URL。
- 历史接口额外返回 `imageObjectKeys`，前端在签名 URL 加载失败时自动重新获取 URL。
- 读取旧的签名 URL 时，仅接受当前配置的 OSS endpoint、bucket 域名或自定义域名，并转换为 object key；未知外部 URL 丢弃。

## 数据库与部署

无需新增 migration。`chat_memory_message.images` 已存在，且原设计就是保存 OSS object key；旧行在读取时兼容转换，不要求停机回填。部署后需重启应用使新的内存存储和前端资源生效，Redis 聊天缓存会在读取时刷新图片 URL。
