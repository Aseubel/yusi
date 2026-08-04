# 访问控制与公开端点安全修复

日期：2026-08-03

## 触发原因

源码审查发现部分接口信任请求参数中的 `userId` 或 `objectKey`，日记、地点和 OSS 资源存在越权访问风险；JWT 认证未区分 access/refresh；WebSocket 和 MCP 端点的默认暴露范围过大。

## 修复范围

- 日记、地点、房间和图片接口统一使用认证上下文中的用户身份。
- 房间详情、报告和状态变更响应增加成员校验与提交内容脱敏，避免仅凭 room code 读取或从操作响应中带出其他成员的未公开叙事。
- 图片 URL、删除、MD5 秒传和分片状态增加用户归属校验；分片状态按用户隔离并限制参数范围。
- OSS 图片访问改为 SDK 生成的短时签名 URL，Bucket 不应再依赖公开读取。
- 用户实体不再序列化密码哈希、备份密钥和密钥盐值。
- 受保护 HTTP 接口只接受 `type=access` 且仍有效的设备 Token；刷新令牌采用 Redis 原子轮换并停止写入日志。
- WebSocket CONNECT 校验 access token，订阅按匹配关系或房间成员关系限制（包括房间 `/status` 子主题），前端 STOMP 客户端发送认证头。
- MCP 同一 Go 进程拆分公共 `/mcp` 与内部 `/internal/mcp` 入口：公共入口只暴露 `diarySearch`/`memorySearch` 并要求用户 Developer API Key；内部入口只暴露 `web_search` 并要求 `X-MCP-Service-Key`。用户开发者 Key 仅通过 `Authorization`/`X-Developer-API-Key` 透传到 Java scope 鉴权；API Key 不再从查询参数读取；gRPC 端口改为仅容器网络暴露。
- MCP 公共入口不再内置 CORS 或来源登记；CORS 不是 Agent API 的认证机制。若浏览器页面未来需要直连，由 HTTPS 反向代理按需配置 CORS。
- 登录、验证码、找回密码、上传、聊天和公开资源入口补充 Redis 分布式限流；公开 Feed 限制分页范围，限流拒绝使用 HTTP 429。
- 限流和接口用量统计默认使用连接对端地址；只有配置 `YUSI_TRUSTED_PROXY_ADDRESSES` 后才读取可信代理转发的客户端地址，避免伪造 `X-Forwarded-For` 绕过限流。
- 建议详情接口改为管理员可见。
- 人生图谱合并建议和用户通知的单条操作增加 `id + userId` 归属条件，避免通过自增 ID 跨用户修改或删除。
- 修正分片上传方法的异常处理括号，并让授权异常按错误码返回对应 HTTP 状态（越权为 403）。
- 开发和生产配置不再内置数据库密码、JWT、加密、OSS 或外部服务密钥占位值；这些值必须由环境变量提供。

## 仍需关注

生产环境必须设置 `MCP_AUTH_API_KEY`、`YUSI_JWT_SECRET`、加密密钥和各外部服务凭据，并通过反向代理限制管理端和 WebSocket 的来源。若部署在可信反向代理后，应将代理出口地址配置到 `YUSI_TRUSTED_PROXY_ADDRESSES`，多个地址使用逗号分隔；未配置时系统故意不信任转发头。OSS Bucket 应保持私有，并继续监控签名 URL 的有效期和泄露情况。本记录只反映源码级修复，尚未进行线上渗透测试。
