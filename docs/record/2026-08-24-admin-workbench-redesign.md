# Yusi 管理端工作台重构实现记录

日期：2026-08-24

## 关联文档

- Spec: `docs/superpowers/specs/2026-08-24-admin-workbench-redesign-design.md`
- Plan: `docs/superpowers/plans/2026-08-24-admin-workbench-redesign.md`

## 已交付

- 管理端统一为分组侧栏、共享页头、工具栏、表格、分页、状态、空状态、图表和详情 Sheet。
- 总览展示真实调用次数、输入/输出/总 Token、成功率、Fallback、平均延迟、P95、成本、运行状态、趋势图和近期失败；无数据时显示空状态。
- 总览最近 7 天筛选按浏览器本地时间生成，避免把无时区的时间参数错误转换成 UTC 后造成统计窗口偏移。
- 调用活动支持场景、模型、用户、运行、Tier、Provider、状态、Fallback 和时间范围筛选，筛选状态写入 URL，并提供页码、页大小、跳页、统计摘要和低敏详情。
- Prompt 新建、编辑和只读查看均改为右侧 Sheet，关闭脏表单会确认放弃修改；用户权限、情景审核、建议回复、通知发布和审计详情统一使用 Sheet 工作流。
- 模型指标改为数据库聚合，新增 `/api/model/metrics/trend`；新增 H2 JPA 回归测试覆盖聚合、空结果、可空 Token/成本、状态分类和趋势分桶。生产 MySQL 使用 `DATE_FORMAT`，H2 测试使用 `FORMATDATETIME`。
- Sheet 对 Select Portal 的点击保持打开，同时遮罩、关闭按钮和 Escape 仍可关闭；Provider/Protocol 与 Tier 编辑包含在脏状态判断中。

## 验证证据

- `frontend`: `pnpm test`：10 个测试文件、41 个测试通过。
- `frontend`: `pnpm exec tsc -b`：退出码 0。
- `frontend`: 管理端目标文件 ESLint：退出码 0。
- `frontend`: `pnpm build`：退出码 0，Vite 构建完成并生成 PWA precache 50 entries。
- `frontend`: `pnpm lint`：退出码 1，仅报告既有无关文件 `src/components/Layout.tsx` 和 `src/pages/Messages.tsx` 的 3 个 React 规则错误；本任务未修改这两个用户端文件。
- `backend`: `mvn "-Dtest=ModelCallTraceMetricsRepositoryTest,ModelManagementServiceTest,ModelManagementControllerTest" test`：9 tests、0 failures、0 errors，BUILD SUCCESS。
- `backend`: `mvn "-DskipTests" compile`：BUILD SUCCESS。
- `git diff --check` 及 `frontend` 嵌套仓库的 `git diff --check`：无输出、退出码 0。
- 本地开发服务已启动于 `http://127.0.0.1:5174/`。浏览器访问 `/admin` 被现有登录守卫重定向到 `/login`；当前没有管理员会话，也没有发现明确的开发 mock 入口，因此 Provider/Protocol、Prompt 脏关闭和移动端页面的认证后浏览器交互未能实际执行。

## 数据与安全边界

模型汇总和趋势来自 `model_call_trace` 的数据库 `COUNT/SUM/AVG` 聚合，趋势不补造缺失时间桶；无数据时前端显示诚实空状态。调用详情沿用低敏 DTO，只展示现有的调用、路由、模型、状态、Token、成本、延迟和错误分类字段，没有新增 Prompt 正文、回答、思考内容、图片 URL、API key 或完整上游响应体暴露。

P95 在有效延迟样本不足 20 条时返回空值，前端显示不可用状态，不伪造精度；已知成本与未知成本数量分开显示。

## 已知限制

- 完整 `pnpm lint` 的 3 个错误位于本任务范围外的既有用户端文件，未为本次管理端重构扩大修改范围。
- 浏览器认证后验收需要一个现有的管理员登录会话；本次可验证到登录守卫和本地开发服务，但无法在无凭据条件下进入管理页面执行交互回归。
