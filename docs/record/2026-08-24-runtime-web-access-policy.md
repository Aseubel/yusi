# 运行时 Web 访问策略记录

日期：2026-08-24

## 背景

生产后端原先只从配置文件读取一个固定 `allowed-origin`。本地 Vite 页面直接访问生产 API 时，预检请求会在进入管理 Controller 前返回 `403 Invalid CORS request`；每次临时调试都修改配置文件并重启服务，成本高且容易留下过宽的长期配置。

本次为后台增加运行时 Web 访问策略，用于动态管理浏览器 Origin、客户端 IP 和短期本地开发通道。策略由管理员在后台修改并持久化，服务端以不可变快照提供给 HTTP、CORS 和 WebSocket 入口，避免每个请求重复读取数据库。

## 已实现

- 新增 `web_access_policy` 单例表与 Flyway 迁移，存储开发模式、过期时间、Origin/IP 白名单和黑名单、版本及操作人。
- 新增 `GET /api/admin/web-access-policy` 和 `PUT /api/admin/web-access-policy`。
- 查看策略要求管理员权限；修改策略要求超级管理员权限，并写入安全审计事件 `WEB_ACCESS_POLICY_UPDATED / WEB_ACCESS_POLICY`。
- HTTP API、SockJS/native WebSocket 和语音 WebSocket 统一使用运行时策略。
- 黑名单优先；Origin 白名单非空时只允许命中白名单；IP 白名单为空表示不限制 IP。
- 开发模式只增加 `http://localhost:*`、`http://127.0.0.1:*`、`http://[::1]:*`，默认 24 小时，最长 7 天，过期后自动失效。
- 保存时校验当前客户端 IP仍然被允许，避免管理员保存规则后立刻锁死当前入口。
- 服务默认每 5 秒从数据库刷新策略；环境变量仍可提供生产 Origin 与启动期开发模式。
- 管理后台新增“Web 访问控制”页面，展示当前客户端 IP、环境 Origin、策略版本和开发通道状态，支持编辑四类规则以及开发模式有效期；普通管理员只读。
- Vite 代理支持 `VITE_DEV_API_TARGET`，API 和 WebSocket 目标统一从该变量生成，并在转发到后端时移除 `Origin`。因此本地页面可通过同源代理进入后台，先开启临时开发模式，再按需改为直连或继续使用代理。

## 本地调试方式

推荐继续使用 Vite 同源代理：

```powershell
cd frontend
$env:VITE_DEV_API_TARGET = "https://yusi-backend.aseubel.cn"
pnpm dev
```

打开 `http://localhost:5174/admin/web-access`，使用超级管理员登录，在“Web 访问控制”中开启本地开发 Origin，并设置失效时间。需要限制来源 IP 时，在 IP 白名单或黑名单中每行填写一个 IPv4/IPv6 地址或 CIDR 网段。

如果本地后端运行在其他地址，可将 `VITE_DEV_API_TARGET` 设置为例如 `http://127.0.0.1:8080`；WebSocket 代理会自动使用对应的 `ws`/`wss` 协议。

## 配置迁移

已有的 `YUSI_WEB_ALLOWED_ORIGIN` 仍是启动期生产基线，不会被运行时策略页面删除或覆盖。新增配置如下：

- `YUSI_WEB_DEV_MODE_ENABLED`：启动期是否临时开启本地开发 Origin，默认 `false`。
- `YUSI_WEB_POLICY_REFRESH_MS`：策略刷新间隔，默认 `5000`。
- `VITE_DEV_API_TARGET`：仅本地 Vite 代理使用的后端 HTTP(S) 地址。

生产环境保持 `YUSI_WEB_DEV_MODE_ENABLED=false`，优先使用后台中带过期时间的开发模式开关；不要把 `*` 写入 Origin 规则，服务端会拒绝该值。

## 验证

- `frontend`: `pnpm build`，退出码 0，新增 Web 访问控制页面成功产出懒加载 chunk。
- `frontend`: locale JSON 解析通过。
- `frontend`: Vite 开发服务可在 `http://127.0.0.1:5174/` 启动。
- `frontend`: `pnpm lint` 仍受既有 `src/components/Layout.tsx` 和 `src/pages/Messages.tsx` 的 3 个 React 规则错误影响，本次未修改这些文件。
- `backend`: `mvnw.cmd "-Dtest=RuntimeAccessPolicyEvaluatorTest,DynamicCorsConfigurationSourceTest,WebAccessPolicyFilterTest,RuntimeOriginHandshakeInterceptorTest,WebCorsConfigTest" test`，退出码 0。
- `backend`: `mvnw.cmd -q test` 全量测试退出码 0；认证覆盖、限流覆盖和 MockMvc 边界测试均通过。
- 根仓库与前端嵌套仓库均通过 `git diff --check`。

## 已知边界

- 通过同源 Vite 代理时，后端看到的是代理连接的客户端地址；直接跨域访问时，后端按 `ClientIpResolver` 的可信代理配置解析来源地址。
- 未提供管理员凭据，因此没有在真实登录会话下执行页面保存和 WebSocket 端到端验收；API、策略计算、CORS、入口过滤和前端生产构建均已验证。
