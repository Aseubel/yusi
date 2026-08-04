# Yusi 记忆透明度与生命周期

> **Status:** Phase 1 slice complete
> **Date:** 2026-08-04
> **Related:** [产品与工程演进计划](../plans/2026-08-04-yusi-agent-product-roadmap.md) · [产品需求](../../prd/2026-08-04-memory-transparency-lifecycle.md) · [工程设计](../../superpowers/specs/2026-08-04-memory-transparency-long-term-design.md) · [实现计划](../../superpowers/plans/2026-08-04-long-term-memory-transparency.md)

## 文档关系

本记录是 roadmap Phase 1“记忆信任与生命周期”的实现结果；PRD 定义用户能力、产品边界和验收标准，工程设计定义数据与运行时约束，实现计划记录任务拆分。四份文档共同描述同一切片，不新增独立产品入口。

## 范围

本次完成三类派生认知的透明度和生命周期控制：

- `MidTermMemory`：来自日记、对话和广场等来源的中期摘要。
- `UserPersona`：用户稳定偏好和交互需求的单行稳定画像。
- 现有 `LifeGraph` 关系图谱实体：继续使用既有图谱模型和 `LifeGraphMention` 证据，不新增“人生图谱”页面或第二套图谱。

原始日记、聊天消息、Prompt、工具参数和工具结果不属于记忆中心的返回内容。删除操作只删除派生认知，不删除来源内容。

## 用户能力

记忆中心页面位于 `/memory-center`，通过页内分段控制管理中期记忆、稳定画像和关系图谱：

- 查看安全摘要、来源类型、来源 ID、形成/更新时间、有效期、置信度和匹配使用范围。
- 编辑中期记忆摘要，或编辑稳定画像允许的高层字段。
- 调整有效期，恢复为永不过期，隐藏/恢复和删除派生认知。
- 独立设置是否允许中期记忆、稳定画像和关系图谱实体进入匹配画像。
- 查看关系图谱实体的安全来源引用，只返回日记 ID、日期和时间，不返回 mention 原文片段。

所有变更都按 `id + userId` 归属校验。关系图谱实体删除会在同一用户范围内清理别名、关系、mention 和合并判断记录；中期记忆的向量副本删除失败只记录告警，不阻断数据库遗忘操作。

## API

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `GET` | `/api/memory/center?limit=50` | 获取中期记忆及状态统计 |
| `PATCH` | `/api/memory/center/{id}` | 更新中期记忆摘要或生命周期 |
| `DELETE` | `/api/memory/center/{id}` | 删除当前用户自己的中期记忆 |
| `GET` | `/api/memory/persona` | 获取安全稳定画像及生命周期 |
| `PATCH` | `/api/memory/persona` | 更新画像字段或生命周期控制 |
| `DELETE` | `/api/memory/persona` | 删除当前用户的派生画像 |
| `GET` | `/api/memory/life-graph?limit=50` | 获取安全关系图谱实体及来源引用 |
| `PATCH` | `/api/memory/life-graph/{id}` | 更新关系图谱实体生命周期 |
| `DELETE` | `/api/memory/life-graph/{id}` | 删除当前用户的派生图谱实体及依赖 |

既有 `/api/lifegraph` 后端命名空间继续服务关系图谱业务 API，但它不是新的前端产品入口。

## 生命周期规则

1. 新生成的中期记忆、稳定画像和关系图谱实体默认 `match_allowed=false`；历史记录迁移保留既有匹配行为。
2. 隐藏或过期的 Persona 不进入聊天上下文、认知冲突检查、报告、成长统计或匹配。
3. 隐藏或过期的关系图谱实体不进入图谱可视化、时间线/洞察、聊天上下文或匹配。
4. `match_allowed=false` 只阻断匹配，仍允许其他被授权的可见 Agent 用途。
5. 中期记忆的隐藏、过期、合并和未授权状态会从检索和匹配读取路径排除。
6. 生命周期变更会尽力刷新匹配画像；刷新失败不回滚用户的隐藏、授权、有效期或删除选择。
7. 图谱搜索和 Agent 图谱工具只返回实体摘要、关系和安全来源元数据，不再返回 `LifeGraphMention.snippet`。

## 入口边界

- `/community` 是唯一关系图谱前端入口。
- `/lifegraph` 前端页面已移除，不做重定向或兼容别名。
- `Timeline`、`Soul Report` 和 `Agent Growth` 本次保持独立页面。
- 统一洞察 Hub 作为后续产品演进方向，不在本次切片内实现。

## 已知限制

- Persona 仍按单行稳定结构控制，字段级来源和字段级生命周期留待后续验证。
- 关系图谱关系边没有独立生命周期控制。
- `valid_until` 由用户直接调整，定时清理和更完整的遗忘审计不在本次范围。
- 原始来源编辑不会强制回收全部历史派生认知，用户可在记忆中心逐条修正或删除。

## 验证

- 后端 `.\mvnw.cmd -q test` 通过；新增/聚焦覆盖 Persona、关系图谱归属与过滤、生命周期删除清理、匹配范围和安全来源。
- 前端 `pnpm test` 通过：3 个测试文件、10 个测试。
- 前端 `pnpm build` 通过。
- Memory Center 新增文件独立 `pnpm exec eslint src/pages/MemoryCenter.tsx src/lib/memoryCenter.ts src/lib/memoryCenter.test.ts` 通过。
- 路由审计确认 `/community` 1 个、`/lifegraph` 0 个。
- 全量 `pnpm lint` 仍受既有其他页面的 45 个 error、6 个 warning 阻断；新增 Memory Center 页面和两个生命周期面板不在失败项中。
