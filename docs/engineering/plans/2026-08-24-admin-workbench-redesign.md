# Admin Workbench Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `/admin/*` 重构为真实可用的管理工作台，让模型总览能解释调用量、Token、成功率、Fallback、延迟和成本，让调用活动具备完整查询分页，并让所有编辑、审核和处理流程在统一的右侧 Panel 中完成。

**Architecture:** 保留现有 React hooks、React Router、Axios、i18next、Spring MVC 和 Spring Data JPA。前端新增管理端专用 primitive 与纯逻辑工具，所有列表查询状态同步到 URL；后端将模型 Trace 汇总和时间分桶下沉到数据库查询，继续通过低敏 DTO 投影，避免把时间范围内的全部 Trace 载入 JVM。模型治理的现有 console、draft、route preview 和 runtime reset 契约继续复用，只扩展指标与界面投影。

**Tech Stack:** React 19, TypeScript, Vite, Vitest, Tailwind CSS, Radix Dialog/Select, lucide-react, i18next, Axios, Spring Boot 3.4, Spring Data JPA, MySQL 8, H2 test profile, Java 21, Maven。

## Global Constraints

- 不引入新的请求状态管理框架；继续使用现有 Axios、React hooks、React Router 和 i18next。
- 不凭空补充没有后端来源的趋势或统计数据；没有数据时展示诚实的空状态。
- 不把管理端重构扩展为新的业务领域，不修改用户端核心体验。
- 不新增过程性文档；本任务只维护本 spec、一个 implementation plan 和一个 implementation record。
- 管理端 surfaces 使用不超过 8px 的圆角、轻量边框和稳定阴影，不使用大面积玻璃效果、卡片 hover 位移或装饰性渐变。
- 管理端列表必须覆盖 loading、empty、error/retry、saving、success 和 destructive 状态；刷新、筛选、分页和返回页面必须恢复 URL 查询上下文。
- 统计、详情、日志和 tooltip 不得暴露 Prompt 正文、回答、思考内容、图片 URL、API key 或完整上游响应体。
- 代码与界面文案优先使用现有项目模式；新增文件使用 ASCII 标点，中文文案只写入已有 UTF-8 locale 文件。

---

## File Map

### Frontend files

- Create: `frontend/src/components/admin/AdminPageHeader.tsx` for title, description, refresh and one primary action.
- Create: `frontend/src/components/admin/AdminToolbar.tsx` for URL-backed filters and secondary actions.
- Create: `frontend/src/components/admin/AdminSurface.tsx` for stable bordered admin sections without the global glass Card behavior.
- Create: `frontend/src/components/admin/AdminTable.tsx` for desktop table headers, row spacing and mobile list slots.
- Create: `frontend/src/components/admin/AdminPagination.tsx` for total/range/page-size/page-number navigation.
- Create: `frontend/src/components/admin/AdminDetailSheet.tsx` for read-only, edit, review and reply panels.
- Create: `frontend/src/components/admin/AdminStatusBadge.tsx` for shared status colors and labels.
- Create: `frontend/src/components/admin/AdminEmptyState.tsx` for empty and filtered-empty states.
- Create: `frontend/src/components/admin/AdminChartPanel.tsx` for accessible real-data bar/line summaries and equivalent tables.
- Create: `frontend/src/components/admin/index.ts` for the admin component barrel exports.
- Create: `frontend/src/lib/admin/pagination.ts` for page normalization, page windows and range text.
- Create: `frontend/src/lib/admin/queryState.ts` for URL query serialization and parsing.
- Create: `frontend/src/lib/admin/metrics.ts` for token, count, percentage, cost and time formatting.
- Create: `frontend/src/lib/admin/pagination.test.ts`, `frontend/src/lib/admin/queryState.test.ts`, and `frontend/src/lib/admin/metrics.test.ts` for pure logic tests.
- Modify: `frontend/src/lib/api.ts` to type all admin list/detail endpoints and the model metrics/trend contract.
- Modify: `frontend/src/components/admin/AdminLayout.tsx` to use grouped workbench navigation and the shared page shell.
- Modify: `frontend/src/components/ui/Select.tsx` and `frontend/src/components/ui/Sheet.tsx` to make Select Portal interactions safe inside Sheet.
- Modify: `frontend/src/pages/admin/AdminDashboard.tsx` for the operations home and queue links.
- Modify: `frontend/src/pages/admin/UserManagement.tsx`, `ScenarioAudit.tsx`, `SuggestionManagement.tsx`, `NotificationManagement.tsx`, and `SecurityAudit.tsx` to use shared list, pagination and detail-panel behavior.
- Modify: `frontend/src/pages/admin/PromptManagement.tsx` to remove the inline editor and use a dirty-aware editor/detail Sheet.
- Modify: `frontend/src/pages/admin/ModelManagement.tsx` and all files under `frontend/src/pages/admin/model-management/` for metrics, charts, activity pagination, registry panels and Select behavior.
- Modify: `frontend/src/i18n/locales/zh.json` and `frontend/src/i18n/locales/en.json` for the new workbench, metric, state, error and confirmation copy.

### Backend files

- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelMetricAggregate.java` for the database aggregate projection used by the service.
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelMetricBucket.java` for one hourly/daily aggregate bucket.
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelMetricTrendResponse.java` for bucket metadata and ordered trend items.
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelMetricTrendQuery.java` for trend bucket and shared Trace filters.
- Create: `src/main/java/com/aseubel/yusi/repository/ModelCallTraceMetricsRepository.java` for aggregate and trend query methods.
- Create: `src/main/java/com/aseubel/yusi/repository/ModelCallTraceMetricsRepositoryImpl.java` for Criteria API/native database grouping without loading all traces.
- Modify: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelMetricSummary.java` to expose `callCount` and `totalTokens` while retaining the existing low-sensitivity fields.
- Modify: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelCallTraceQuery.java` to clamp page/size values and keep the existing attempt filters aligned with the metric filter names.
- Modify: `src/main/java/com/aseubel/yusi/repository/ModelCallTraceRepository.java` to include the custom metrics repository fragment.
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelManagementService.java` to call database aggregation and expose trend data.
- Modify: `src/main/java/com/aseubel/yusi/controller/ModelManagementController.java` to expose `GET /api/model/metrics/trend` with admin authorization and validation.
- Modify: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelCallTraceItem.java` to add `runId` to the activity projection so the detail Sheet can support the existing run filter without exposing message content.
- Modify: `src/test/java/com/aseubel/yusi/service/ai/model/ModelManagementServiceTest.java` and `src/test/java/com/aseubel/yusi/controller/ModelManagementControllerTest.java` for aggregate, trend, pagination and authorization contracts.

### Single implementation record

- Create only after implementation verification: `docs/record/2026-08-24-admin-workbench-redesign.md`.

No roadmap file, additional design/spec/plan/record, deployment note or bug note is part of this task.

## Task 1: Establish URL, pagination and metric utility contracts

**Files:**
- Create: `frontend/src/lib/admin/pagination.ts`
- Create: `frontend/src/lib/admin/queryState.ts`
- Create: `frontend/src/lib/admin/metrics.ts`
- Test: `frontend/src/lib/admin/pagination.test.ts`
- Test: `frontend/src/lib/admin/queryState.test.ts`
- Test: `frontend/src/lib/admin/metrics.test.ts`

**Interfaces:**
- `normalizePage<T>(payload: unknown, fallbackSize: number): NormalizedPage<T>` always returns `content`, `totalElements`, `totalPages`, `number`, and `size`.
- `getPageWindow(currentPage: number, totalPages: number, radius?: number): number[]` returns one-based page numbers with no duplicates and no out-of-range values.
- `getPageRange(page: NormalizedPage<unknown>): { from: number; to: number; total: number }` returns `{ from: 0, to: 0, total: 0 }` for empty pages.
- `readAdminQuery<T>(searchParams: URLSearchParams, defaults: T, schema: QuerySchema<T>): T` and `writeAdminQuery<T>(value: T, schema: QuerySchema<T>): URLSearchParams` preserve supported filter, page and size fields while ignoring unknown keys.
- `formatCompactNumber`, `formatTokens`, `formatPercent`, `formatCurrency`, and `formatDuration` are deterministic pure functions used by cards, charts and tables.

- [ ] **Step 1: Write failing pagination and formatting tests.**

```ts
import { describe, expect, it } from 'vitest'
import { getPageRange, getPageWindow, normalizePage } from './pagination'
import { formatPercent, formatTokens } from './metrics'

describe('admin pagination', () => {
  it('normalizes a Spring page payload', () => {
    expect(normalizePage({ content: [{ id: 1 }], totalElements: 41, totalPages: 5, number: 2, size: 10 }, 20))
      .toEqual({ content: [{ id: 1 }], totalElements: 41, totalPages: 5, number: 2, size: 10 })
  })

  it('returns a compact one-based page window', () => {
    expect(getPageWindow(5, 10, 2)).toEqual([1, 4, 5, 6, 7, 8, 10])
    expect(getPageWindow(0, 0)).toEqual([])
  })

  it('does not claim a range for an empty page', () => {
    expect(getPageRange({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }))
      .toEqual({ from: 0, to: 0, total: 0 })
  })
})

describe('admin metric formatting', () => {
  it('formats percentages and tokens without inventing precision', () => {
    expect(formatPercent(0.936)).toBe('93.6%')
    expect(formatTokens(12500)).toBe('12.5K')
  })
})
```

- [ ] **Step 2: Run the focused tests to confirm the utilities are absent.**

Run from `frontend`:

```bash
pnpm vitest run src/lib/admin/pagination.test.ts src/lib/admin/queryState.test.ts src/lib/admin/metrics.test.ts
```

Expected: FAIL because the three utility modules and exported functions do not exist.

- [ ] **Step 3: Implement the normalization and query helpers.**

Use the following behavior as the contract:

```ts
export interface NormalizedPage<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export const normalizePage = <T>(payload: unknown, fallbackSize: number): NormalizedPage<T> => {
  const value = (payload && typeof payload === 'object' ? payload : {}) as Record<string, unknown>
  const nested = value.page && typeof value.page === 'object' ? value.page as Record<string, unknown> : value
  const content = Array.isArray(nested.content) ? nested.content as T[] : []
  const size = typeof nested.size === 'number' && nested.size > 0 ? nested.size : fallbackSize
  const totalElements = typeof nested.totalElements === 'number' ? Math.max(0, nested.totalElements) : 0
  const totalPages = typeof nested.totalPages === 'number' ? Math.max(0, nested.totalPages) : Math.ceil(totalElements / size)
  const number = typeof nested.number === 'number' ? Math.max(0, nested.number) : 0
  return { content, totalElements, totalPages, number, size }
}

export const getPageWindow = (currentPage: number, totalPages: number, radius = 2): number[] => {
  if (totalPages <= 0) return []
  const pages = new Set<number>([1, totalPages])
  for (let page = currentPage + 1 - radius; page <= currentPage + 1 + radius; page += 1) {
    if (page >= 1 && page <= totalPages) pages.add(page)
  }
  return [...pages].sort((left, right) => left - right)
}
```

Query serializers must encode booleans as `true`/`false`, omit empty strings and nulls, clamp page to `>= 0`, and use only declared fields. Formatting must return `-` for null/undefined values and must use `Intl.NumberFormat` with stable `en-US` numeric grouping.

- [ ] **Step 4: Run the focused tests and lint the new modules.**

Run:

```bash
pnpm vitest run src/lib/admin/pagination.test.ts src/lib/admin/queryState.test.ts src/lib/admin/metrics.test.ts
pnpm exec eslint src/lib/admin
```

Expected: all focused tests pass and ESLint reports no errors.

## Task 2: Move model metrics and trend aggregation into the database

**Files:**
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelMetricAggregate.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelMetricBucket.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelMetricTrendResponse.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelMetricTrendQuery.java`
- Create: `src/main/java/com/aseubel/yusi/repository/ModelCallTraceMetricsRepository.java`
- Create: `src/main/java/com/aseubel/yusi/repository/ModelCallTraceMetricsRepositoryImpl.java`
- Modify: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelMetricSummary.java`
- Modify: `src/main/java/com/aseubel/yusi/repository/ModelCallTraceRepository.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelManagementService.java`
- Modify: `src/main/java/com/aseubel/yusi/controller/ModelManagementController.java`
- Test: `src/test/java/com/aseubel/yusi/service/ai/model/ModelManagementServiceTest.java`
- Test: `src/test/java/com/aseubel/yusi/controller/ModelManagementControllerTest.java`
- Test: `src/test/java/com/aseubel/yusi/repository/ModelCallTraceMetricsRepositoryTest.java`

**Interfaces:**
- `ModelCallTraceMetricsRepository.aggregate(Specification<ModelCallTrace> specification)` returns one `ModelMetricAggregate` using database `COUNT`, `SUM`, `AVG` and conditional `SUM` expressions.
- `ModelCallTraceMetricsRepository.aggregateTrend(Specification<ModelCallTrace> specification, ModelMetricTrendQuery.Bucket bucket)` returns ordered `ModelMetricBucket` rows grouped by MySQL `DATE_FORMAT` hour/day buckets.
- `ModelManagementService.getMetrics(ModelCallTraceQuery query)` maps the aggregate to `ModelMetricSummary` and never calls `findAll(specification)`.
- `ModelManagementService.getMetricTrend(ModelMetricTrendQuery query)` returns `ModelMetricTrendResponse` with `bucket`, `from`, `to`, and ordered `items`.
- `GET /api/model/metrics/trend` accepts the existing Trace filters plus `bucket=HOUR|DAY` and is admin-only.
- `ModelMetricSummary` uses `callCount` as the canonical call total and adds `totalTokens = inputTokens + outputTokens`; `routeCount` is removed from the frontend contract and is not used as a route-rule count.

- [ ] **Step 1: Add service tests that prove aggregation is used.**

Add a custom repository mock to `ModelManagementServiceTest` and assert the service maps every aggregate field:

```java
@Mock
private ModelCallTraceMetricsRepository metricsRepository;

@Test
void mapsDatabaseAggregateWithoutLoadingAllTraces() {
    when(metricsRepository.aggregate(any())).thenReturn(new ModelMetricAggregate(
            42L, 5L, 0.119D, 0.952D, 180D, 420D,
            2L, 2L, 120_000L, 30_000L, new BigDecimal("1.25"), 3L));

    ModelMetricSummary result = service.getMetrics(ModelCallTraceQuery.builder().provider("openai").build());

    assertThat(result.getCallCount()).isEqualTo(42L);
    assertThat(result.getTotalTokens()).isEqualTo(150_000L);
    assertThat(result.getInputTokens()).isEqualTo(120_000L);
    assertThat(result.getOutputTokens()).isEqualTo(30_000L);
    assertThat(result.getKnownCost()).isEqualByComparingTo("1.25");
    verify(metricsRepository).aggregate(any());
    verify(modelCallTraceRepository, never()).findAll(any(Specification.class));
}
```

The test setup must inject the new repository fragment into `ModelManagementService`; the existing filtering test remains and continues to assert that user and run ID predicates are case-insensitive.

- [ ] **Step 2: Run the new focused backend tests and confirm the mapping fails first.**

Run:

```powershell
./mvnw.cmd -Dtest=ModelManagementServiceTest test
```

Expected: FAIL until the aggregate DTO, repository fragment and service mapping are implemented.

- [ ] **Step 3: Implement the aggregate DTO and repository fragment.**

Use an immutable aggregate projection with this field order:

```java
public record ModelMetricAggregate(
        long callCount,
        long fallbackCount,
        double fallbackRate,
        double successRate,
        double averageLatencyMs,
        Double p95LatencyMs,
        long rateLimitedCount,
        long errorCount,
        long inputTokens,
        long outputTokens,
        BigDecimal knownCost,
        long unknownCostCount) {}
```

The custom repository implementation must build predicates from the existing `Specification<ModelCallTrace>`, select the aggregate expressions in one query, normalize nullable token/cost sums to zero, and classify success using the same values as `ModelCallStatus.isSuccess`: `SUCCESS`, `SUCCEEDED`, `COMPLETED`, `OK`. Rate-limited counts must match the existing `429` or `RATE` error/status rule. Error count is `callCount - successCount`, clamped at zero. The repository integration test runs the same Criteria queries against H2 and covers empty aggregates, nullable usage/cost, status classification and trend buckets; H2 uses `FORMATDATETIME` while production MySQL uses `DATE_FORMAT`.

P95 is calculated by a database-only ordered latency lookup: first count non-null non-negative latency rows, return `null` for fewer than 20 samples, otherwise query the row at `ceil(count * 0.95) - 1` using `setFirstResult` and `setMaxResults(1)`. No latency list may be materialized in Java.

For `aggregateTrend`, group by `DATE_FORMAT(created_at, '%Y-%m-%d %H:00:00')` for `HOUR` and `DATE_FORMAT(created_at, '%Y-%m-%d 00:00:00')` for `DAY`. Return `callCount`, `successCount`, `errorCount`, `fallbackCount`, `inputTokens`, `outputTokens`, and `averageLatencyMs` for each bucket. Trend queries must use the same predicates and must not synthesize missing buckets on the server.

- [ ] **Step 4: Wire the service and controller contract.**

Update `ModelMetricSummary` with `callCount` and `totalTokens`, map `null` aggregate results to zero values, and replace the current `findAll(buildSpecification(safeQuery))` implementation. Add `ModelMetricTrendQuery` validation for `bucket`, `from < to`, non-negative token/filter values, and a maximum range of 366 days. Return HTTP 400 through the existing `BusinessException` path for invalid query values.

Add the controller method:

```java
@GetMapping("/metrics/trend")
public Response<ModelMetricTrendResponse> metricTrend(@Valid @ModelAttribute ModelMetricTrendQuery query) {
    checkAdmin();
    return Response.success(modelManagementService.getMetricTrend(query));
}
```

The default bucket is `HOUR`; the frontend will send explicit `from`, `to`, and `bucket` values for every chart request.

- [ ] **Step 5: Add controller authorization and trend mapping tests.**

Test that a non-admin calling `metricTrend` receives `BusinessException` with the existing admin denial message, and that an admin request delegates the exact query to `getMetricTrend`. Serialize a summary and assert it contains `callCount`, `totalTokens`, and no prompt/response/reasoning/API-key fields.

- [ ] **Step 6: Run backend focused tests and compile.**

Run:

```powershell
./mvnw.cmd -Dtest=ModelManagementServiceTest,ModelManagementControllerTest test
./mvnw.cmd -DskipTests compile
```

Expected: both commands exit 0. The service test must show no `findAll(Specification)` invocation for metrics.

## Task 3: Build shared management primitives and stable visual language

**Files:**
- Create: `frontend/src/components/admin/AdminPageHeader.tsx`
- Create: `frontend/src/components/admin/AdminToolbar.tsx`
- Create: `frontend/src/components/admin/AdminSurface.tsx`
- Create: `frontend/src/components/admin/AdminTable.tsx`
- Create: `frontend/src/components/admin/AdminPagination.tsx`
- Create: `frontend/src/components/admin/AdminDetailSheet.tsx`
- Create: `frontend/src/components/admin/AdminStatusBadge.tsx`
- Create: `frontend/src/components/admin/AdminEmptyState.tsx`
- Create: `frontend/src/components/admin/AdminChartPanel.tsx`
- Create: `frontend/src/components/admin/index.ts`
- Modify: `frontend/src/components/ui/Select.tsx`
- Modify: `frontend/src/components/ui/Sheet.tsx`

**Interfaces:**
- `AdminPageHeader` accepts `title`, `description`, optional `eyebrow`, `onRefresh`, `refreshing`, and one `primaryAction` node.
- `AdminPagination` accepts `page`, `size`, `totalElements`, `totalPages`, `onPageChange`, `onSizeChange`, and `disabled`, and renders current range, page-size select, numbered page buttons, previous/next icon buttons and a page input.
- `AdminDetailSheet` accepts `open`, `onOpenChange`, `title`, `description`, `children`, optional `footer`, `dirty`, and `onDiscard`; closing a dirty sheet must require confirmation.
- `AdminChartPanel` accepts `title`, `description`, `series`, `labels`, `loading`, `empty`, and `summaryTable`; chart and table must represent the same values.
- `AdminStatusBadge` maps a status code to a translated label and semantic tone without requiring each page to repeat color classes.

- [ ] **Step 1: Add shared components with fixed layout contracts.**

Use `AdminSurface` classes equivalent to `border border-border bg-card shadow-sm rounded-lg`, with no blur, gradient, transform or hover translation. `AdminTable` must keep a stable `min-width` on desktop and expose a mobile row slot; no table row may resize when a loading label, badge or error appears.

`AdminPagination` must calculate range using `getPageRange` and page numbers using `getPageWindow`. A page-size change resets page to 0. The page input accepts one-based values, clamps to `1..totalPages`, and submits on Enter. All icon-only buttons have `aria-label` and `title`.

- [ ] **Step 2: Make the Sheet and Select boundary explicit.**

Set the Radix Select root to non-modal where supported and mark its portaled content with `data-admin-select-content`. In `SheetContent`, prevent `pointerDownOutside` and `interactOutside` only when the event target is inside `[data-admin-select-content]`; keep real overlay clicks, the close button and Escape as normal close paths. Do not disable the Sheet overlay globally.

The relevant guard must have this behavior:

```ts
const isSelectPortalTarget = (target: EventTarget | null): boolean =>
  target instanceof HTMLElement && Boolean(target.closest('[data-admin-select-content]'))

onPointerDownOutside={(event) => {
  if (isSelectPortalTarget(event.target)) event.preventDefault()
}}
onInteractOutside={(event) => {
  if (isSelectPortalTarget(event.target)) event.preventDefault()
}}
```

- [ ] **Step 3: Run TypeScript and pure tests before page migration.**

Run from `frontend`:

```bash
pnpm test
pnpm exec tsc -b
```

Expected: existing tests remain green and the new shared component props type-check.

## Task 4: Rebuild the admin shell and operations dashboard

**Files:**
- Modify: `frontend/src/components/admin/AdminLayout.tsx`
- Modify: `frontend/src/pages/admin/AdminDashboard.tsx`
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/i18n/locales/zh.json`
- Modify: `frontend/src/i18n/locales/en.json`

**Interfaces:**
- Navigation groups are `工作台`, `运营`, `内容审核`, `AI 平台`, and `安全`, with one active route indicator and one mobile Sheet menu.
- `AdminDashboard` consumes only `adminApi.getStats()` and links queue metrics to `/admin/scenarios` and `/admin/suggestions`; no frontend-only trend is rendered.
- `adminApi` methods use typed `Page<T>` and `URLSearchParams` rather than page code interpolating URLs.

- [ ] **Step 1: Replace the shell navigation and page heading.**

Keep the existing `AdminGuard` and authorization behavior. Replace duplicated link arrays with a grouped nav model containing `group`, `path`, `labelKey`, and lucide icon. Desktop collapse state and mobile Sheet state must preserve the current route and close after navigation. The content area must have one consistent max-width, spacing scale and background.

- [ ] **Step 2: Refactor the dashboard into an operations-first view.**

Use `AdminPageHeader`, six compact metric surfaces for total users, active users today/7d/30d, diaries, rooms, and pending work, then show two queue tables for pending scenarios and pending suggestions with direct actions. Keep the super-admin embedding full sync as a danger action with `ConfirmDialog`, disabled state and result toast. Do not add a chart unless a corresponding backend field is present in `AdminStats`.

- [ ] **Step 3: Add dashboard copy and run the page-level checks.**

Add matching Chinese and English keys for group labels, queue labels, range labels, error/retry text and full-sync confirmation. Run:

```bash
pnpm exec tsc -b
pnpm exec eslint src/components/admin/AdminLayout.tsx src/pages/admin/AdminDashboard.tsx src/lib/api.ts
```

Expected: no TypeScript or ESLint errors.

## Task 5: Migrate users, scenarios, suggestions, notifications and audit to the workbench pattern

**Files:**
- Modify: `frontend/src/pages/admin/UserManagement.tsx`
- Modify: `frontend/src/pages/admin/ScenarioAudit.tsx`
- Modify: `frontend/src/pages/admin/SuggestionManagement.tsx`
- Modify: `frontend/src/pages/admin/NotificationManagement.tsx`
- Modify: `frontend/src/pages/admin/SecurityAudit.tsx`
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/i18n/locales/zh.json`
- Modify: `frontend/src/i18n/locales/en.json`

**Interfaces:**
- Every page stores filters, page and size in `useSearchParams`; changing a filter sets page to 0; reloading the route reconstructs the same query.
- Every page uses `AdminPageHeader`, `AdminToolbar`, `AdminSurface`, `AdminPagination`, `AdminStatusBadge`, `AdminEmptyState`, and `AdminDetailSheet`.
- `adminApi.getSuggestions`, `getSuggestion`, `replySuggestion`, `updateSuggestionStatus`, and `getAnnouncements` are typed; their response bodies remain existing low-sensitivity entities.

- [ ] **Step 1: Migrate user permission editing to a dirty-aware Sheet.**

Keep the existing self/higher-level guards. The table shows user ID with copy affordance, username, permission, match status and icon actions. Open permission editing in `AdminDetailSheet`; show current level, allowed levels and save loading state. Keep deregistration in `ConfirmDialog`, refresh the current page after success, and show a recoverable error region when list loading fails.

- [ ] **Step 2: Migrate scenario review to a queue and detail Sheet.**

Default the status filter to pending. Use a compact table with title, source, submitted time, status and open action. The detail Sheet shows description, source, create/update time and reject reason. Approve is disabled while saving; reject cannot submit without a trimmed reason and uses a second confirmation. Refresh the current query after either result.

- [ ] **Step 3: Migrate suggestions to list/detail split.**

Remove the current full-page replacement when a suggestion is selected. The list retains status filter and URL pagination. The detail Sheet shows original content, contact, existing reply, reply author/time, a reply textarea for pending items, status Select and optional processing time. Reply and status updates are separate locked async actions, and closing the Sheet keeps list query state.

- [ ] **Step 4: Move notification composition into a publish Sheet.**

The main surface is publish history with title, audience, recipient count, status and published time. The primary action opens a Sheet containing title, body, audience, character count, live preview and fixed footer. Publishing requires the existing confirmation flow, clears the draft on success and reloads page 0. History uses complete pagination even when there is only one page today.

- [ ] **Step 5: Add audit detail and complete pagination.**

The audit table keeps action, outcome, resource type and user ID filters in URL state. Clicking a row opens a low-sensitivity detail Sheet with structured key/value rows, wrapping long values. The list uses `AdminPagination` with total range and page-size controls. Do not render details as an uncontrolled pile of badges.

- [ ] **Step 6: Add copy and run the complete frontend test/lint/build cycle.**

Add status, retry, panel, confirmation and pagination keys to both locales. Run:

```bash
pnpm test
pnpm lint
pnpm build
```

Expected: all existing tests pass, ESLint exits 0, and Vite produces a production build.

## Task 6: Refactor Prompt management into a usable list plus editor/detail Sheets

**Files:**
- Modify: `frontend/src/pages/admin/PromptManagement.tsx`
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/lib/admin/queryState.ts`
- Modify: `frontend/src/i18n/locales/zh.json`
- Modify: `frontend/src/i18n/locales/en.json`

**Interfaces:**
- The main Prompt page always contains the list and toolbar; `showForm` must not render an editor block before the list.
- `PromptManagement` query fields are `name`, `scope`, `locale`, `active`, `page`, and `size` and are restored from URL parameters.
- New/edit actions open `AdminDetailSheet`; clicking a row opens read-only detail and its edit action switches the same Sheet to edit mode.
- The editor compares the normalized form to its initial snapshot and asks for discard confirmation before closing dirty state.

- [ ] **Step 1: Add Prompt query-state and form tests as pure logic.**

Cover these cases in `frontend/src/lib/admin/queryState.test.ts` or a Prompt-specific pure test: active `true`/`false` round-trips, empty filters are omitted, changing scope resets page to 0, and a form with only whitespace changes is not considered dirty.

- [ ] **Step 2: Move editor state out of the list layout.**

Keep the current `PromptTemplate` fields and APIs. Replace the inline `showForm` block with Sheet state containing `mode: 'view' | 'edit'`, selected prompt, form value, initial value, saving state and close-confirmation state. The fixed footer contains cancel/close, activate/delete where applicable, and save/create. Use a monospace textarea for template content, but do not put template content in a list row, metric, tooltip or audit payload.

- [ ] **Step 3: Add list ergonomics and pagination.**

Render name/description, scope, locale, version, active/default status, priority and updated time. Keep desktop table and mobile list layouts, use `AdminEmptyState` for both no records and no filter matches, and disable duplicate requests while loading. A successful create/update returns to the list with the current filter when editing and to page 0 after creating.

- [ ] **Step 4: Verify the Prompt workflow.**

Run:

```bash
pnpm exec tsc -b
pnpm exec eslint src/pages/admin/PromptManagement.tsx src/lib/admin/queryState.ts
```

Then manually verify: new Prompt opens only a right Sheet; a dirty Sheet asks before close; a read-only row detail can enter edit; filtering and page size survive refresh; the template body is never displayed in list metadata.

## Task 7: Rebuild model governance overview and call activity around real metrics

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/pages/admin/ModelManagement.tsx`
- Modify: `frontend/src/pages/admin/model-management/types.ts`
- Modify: `frontend/src/pages/admin/model-management/ModelGovernanceOverview.tsx`
- Modify: `frontend/src/pages/admin/model-management/ModelCallActivity.tsx`
- Modify: `frontend/src/pages/admin/model-management/RuntimeHealthPanel.tsx`
- Modify: `frontend/src/pages/admin/model-management/ModelRegistryPanel.tsx`
- Modify: `frontend/src/pages/admin/model-management/RouteList.tsx`
- Modify: `frontend/src/pages/admin/model-management/RoutePolicyEditor.tsx`
- Modify: `frontend/src/pages/admin/model-management/RoutePreview.tsx`
- Modify: `frontend/src/i18n/locales/zh.json`
- Modify: `frontend/src/i18n/locales/en.json`

**Interfaces:**
- `modelApi.getMetrics` returns `ModelMetricSummary` with `callCount`, `totalTokens`, input/output tokens, success/error/fallback counts, latency, P95, cost and unknown-cost count.
- `modelApi.getMetricTrend(params: ModelMetricTrendQuery)` returns `ModelMetricTrendResponse` for `HOUR` or `DAY` buckets.
- `ModelCallActivity` query state contains `from`, `to`, `scene`, `userId`, `runId`, `modelTier`, `provider`, `model`, `fallbackUsed`, `status`, `page`, and `size`; all fields are URL-backed.
- `ModelGovernanceOverview` receives real summary/trend/failure data and renders no generated series.

- [ ] **Step 1: Extend frontend API types and fetch orchestration.**

Add `callCount`, `totalTokens`, `ModelMetricTrendQuery`, `ModelMetricBucket`, and `ModelMetricTrendResponse` to `frontend/src/lib/api.ts`. Add `modelApi.getMetricTrend`. In `ModelManagement.tsx`, load summary, HOUR trend and recent failure attempts using the same selected time range; expose loading/error/retry independently for the metric region. When no Trace exists, retain cards with `0` and chart panels with an explicit no-data state.

- [ ] **Step 2: Replace the overview cards and fake/derived health calculations.**

The first row must show calls, total tokens with input/output split, success rate, fallback count/rate, average latency/P95 and known cost/unknown-cost count. A second row contains three `AdminChartPanel`s:

1. Calls by bucket with success, error and fallback series.
2. Input/output tokens by bucket.
3. Average latency by bucket with P95 text when available.

Add a runtime status distribution using configured model rows and explicit `UP`, `UNKNOWN`, `HALF_OPEN`, `DOWN`, `DISABLED` labels. Add a recent failure table with failure code, model, scene, timestamp and a link that opens activity with matching filters. Every chart includes a text summary table, exact bucket values on focus/hover, legend labels and an honest empty state.

- [ ] **Step 3: Make activity a complete query surface.**

Add filters for model, provider, tier, scene, status, fallback, user/run ID and datetime range. Above the table show the filtered call count, total tokens, success rate and average latency from `getMetrics(filters)`. The footer must show `X-Y of Z`, page size choices `20/50/100`, page input, numbered page window, previous/next buttons and disabled loading states. Use `normalizePage` rather than assuming `totalPages` exists. Selecting an attempt opens the detail Sheet and never exposes Prompt content, responses, reasoning, image URLs, API keys or full upstream payloads.

- [ ] **Step 4: Keep model registration and route editing inside clear panels.**

Preserve the existing draft save/conflict contract. In the registry table show provider, real model ID, capabilities, enabled state, priority, weight, tier usage, route usage and price availability. New/edit model fields open a Sheet with a stable footer; model ID is read-only during edit. Tier member and strategy editing is visibly separated from physical model fields. Route editor shows scene, risk, priority, primary tier, ordered fallback tiers and each tier's strategy; it does not silently mutate tier strategy.

- [ ] **Step 5: Verify the Select-in-Sheet interaction.**

Run the frontend build, then start the dev server with `pnpm dev --host 127.0.0.1`. In the browser verify at desktop and mobile widths:

- opening Provider, Protocol, scene, tier and fallback Select does not close the editor;
- selecting an item updates the value and leaves the Sheet open;
- clicking the Sheet overlay, close icon or pressing Escape closes it;
- a dirty model Sheet asks before discard;
- a chart with no data shows no invented axis or zero-filled trend line.

## Task 8: Normalize visual details, accessibility and bilingual copy

**Files:**
- Modify: `frontend/src/components/admin/AdminChartPanel.tsx`, `AdminDetailSheet.tsx`, `AdminPagination.tsx`, `AdminStatusBadge.tsx`, and `AdminEmptyState.tsx` to close the accessibility and reduced-motion gaps found during page integration.
- Modify: `frontend/src/i18n/locales/zh.json`
- Modify: `frontend/src/i18n/locales/en.json`

**Interfaces:**
- All new visible strings have matching `zh` and `en` keys.
- All icon-only actions expose an accessible name and tooltip; status is not conveyed by color alone.
- Focus rings remain visible, keyboard navigation works for page input and chart summaries, and reduced-motion users do not lose state information.

- [ ] **Step 1: Add the complete admin copy set.**

Add keys for page header states, filter counts, range text, page-size labels, retry/empty/filtered-empty, save/discard, approval/rejection, publish confirmation, calls, tokens, input/output, success/fallback, P95, unknown cost, trend bucket, runtime status, recent failure and activity detail. Use the existing `modelManagement`, `adminDashboard`, `adminAudit`, `promptManagement`, `suggestionManagement`, `scenarioAudit`, and `notificationManagement` namespaces rather than creating duplicate top-level namespaces.

- [ ] **Step 2: Remove admin-only visual regressions.**

Ensure every admin surface uses stable borders and up to 8px radius. Remove `backdrop-blur`, decorative gradient, hover translate and large rounded-card classes from the management pages. Keep the global `Card` unchanged for user pages unless a management page still imports it accidentally; replace that import with `AdminSurface`.

- [ ] **Step 3: Run locale and accessibility checks.**

Run:

```bash
pnpm exec tsc -b
pnpm lint
```

Expected: no missing translation type errors, no invalid hook or JSX errors, and no ESLint errors. Use keyboard-only manual checks for navigation, filters, Sheets, tables, pagination, confirmations and chart summary tables.

## Task 9: Full verification and create the single implementation record

**Files:**
- Create only after all implementation tasks pass: `docs/record/2026-08-24-admin-workbench-redesign.md`
- Inspect: all files changed by Tasks 1-8

**Interfaces:**
- The implementation record is the only new record document and contains observed evidence, not planned claims.
- The record links the existing spec and this plan, summarizes shipped API/UI behavior, records validation commands and their exit results, and lists any residual limitation such as unavailable P95 or no-data charts.

- [ ] **Step 1: Run focused backend verification.**

Run:

```powershell
./mvnw.cmd -Dtest=ModelManagementServiceTest,ModelManagementControllerTest test
```

Expected: focused model management tests pass, including database aggregate mapping, trend authorization, sensitive-field projection and attempt-filter predicates.

- [ ] **Step 2: Run full frontend verification.**

Run from `frontend`:

```bash
pnpm test
pnpm lint
pnpm build
```

Expected: all Vitest tests pass, ESLint exits 0, and the production build completes.

- [ ] **Step 3: Run backend compile and repository hygiene checks.**

Run:

```powershell
./mvnw.cmd -DskipTests compile
git diff --check
```

Expected: compile exits 0 and `git diff --check` prints no whitespace errors. Inspect the final diff for accidental changes to user-facing pages, sensitive DTOs, API keys, prompt bodies, responses or unrelated documentation.

- [ ] **Step 4: Perform browser acceptance checks.**

Start the frontend with `pnpm dev --host 127.0.0.1` and check desktop `1440x900` and mobile `390x844`:

1. Admin shell groups and active route remain coherent; mobile navigation opens and closes.
2. Dashboard metrics and queue links use real responses and show retry/empty states.
3. Prompt list remains the primary content; new/edit/detail actions are Sheets with dirty-close confirmation.
4. Model overview displays calls, token split, success/fallback, latency/cost and real trend data or a no-data state.
5. Activity filters survive refresh, show total/range/page-size/page-number controls, and open low-sensitivity details.
6. Provider/Protocol Select values do not close model editor; overlay, close and Escape still close it.
7. Scenario review, suggestion reply, notification publish and audit detail all remain in panels with locked async actions.

- [ ] **Step 5: Write the implementation record from evidence.**

Create `docs/record/2026-08-24-admin-workbench-redesign.md` with exactly these sections:

```markdown
# Yusi 管理端工作台重构实现记录

日期：2026-08-24

## 关联文档

- Spec: `docs/superpowers/specs/2026-08-24-admin-workbench-redesign-design.md`
- Plan: `docs/superpowers/plans/2026-08-24-admin-workbench-redesign.md`

## 已交付

记录实际交付的共享组件、页面工作流、模型指标接口、数据库聚合、Sheet/Select 修复和敏感边界，不写未实现内容。

## 验证证据

记录每条命令的实际结果，包括前端测试、lint、build、后端 focused tests、compile、diff check 和浏览器验收视口。

## 数据与安全边界

说明指标来自真实 `model_call_trace` 聚合，趋势无数据时如何显示，以及 DTO/Panel 没有暴露 Prompt、回答、思考、图片 URL、API key 或完整上游响应体。

## 已知限制

只记录验证过程中真实发现的限制，例如 P95 在样本不足或数据库不支持稳定查询时显示为空；不得把未知限制写成已解决。
```

- [ ] **Step 6: Confirm the document count and final status.**

Run:

```powershell
git status --short
```

Expected: among newly produced task documents there is exactly one spec (the existing design spec), one plan (this file), and one record (the implementation record). Do not create, edit or check any roadmap file.

## Self-Review Checklist

- [ ] Spec coverage: overview metrics/charts, call activity filters/pagination, shared admin language, Prompt Sheet, model Select boundary, all management pages, i18n, error states and sensitive boundary each map to a task above.
- [ ] Detail check: each implementation step names its files, concrete behavior, runnable command and expected result; no step relies on an unspecified future decision.
- [ ] Type consistency: `ModelMetricAggregate` fields map to `ModelMetricSummary`; `ModelMetricTrendQuery` maps to `/metrics/trend`; `NormalizedPage` maps to `AdminPagination`; `ModelMetricTrendResponse` maps to `AdminChartPanel`.
- [ ] Scope check: no new state-management framework, no user-page visual migration, no roadmap update, and no extra process document.
- [ ] Verification check: completion is claimed only after the commands and browser checks in Task 9 have actual passing evidence.
