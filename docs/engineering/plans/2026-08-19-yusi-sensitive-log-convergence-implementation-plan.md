# 敏感明文日志收敛实施计划

> **For agentic workers:** Execute this plan inline after design review. The repository instructions prohibit subagents and auto-review for this work. Steps use checkbox syntax and each step ends with an explicit verification command.

**Goal:** 在不改变搜索输入、权限、返回值和失败回退行为的前提下，移除生产日志中的 query/keyword、完整工具过滤参数、用户派生正文和异常正文，并用运行时捕获与全量源码审计证明低敏边界。

**Architecture:** 新增无状态的 `LowSensitivityLogSummary` 纯函数工具，只输出固定长度桶和异常类型。四个指定搜索组件使用固定事件名、低敏用户 ID、结果/分页/过滤元数据和固定失败分类；日志不传 query、完整 `expr` 或 `Throwable`。推荐范围同时处理勘察发现的直接用户/模型正文日志，测试使用 Logback `ListAppender` 检查成功和异常路径，功能回归沿用 Mockito 窄测试。

**Tech Stack:** Java 21, Spring Boot 3.4.5, SLF4J/Logback, JUnit 5, Mockito, Maven Wrapper。

## Global Constraints

- 本计划执行阶段不启动应用服务、H2/Milvus/Redis/MySQL/OSS 或其他外部依赖；只运行 Maven 测试命令。
- 不修改 roadmap、CI、migration、既有 Phase 4 fixture/loader/report、`QualityGatePolicy` 或 post-release backlog。
- 任何日志、测试断言、fixture、报告和异常处理都不得打印或持久化用户 query、记忆正文、Prompt、工具参数/结果、昵称、地点名、设备字符串、密钥或异常正文/堆栈。
- 不使用普通无密钥 query hash、query 片段、query 前后缀或 `SensitiveDataMaskService` 作为日志脱敏方案。
- 日志允许的摘要字段只有低敏 ID、固定长度桶、过滤/存在性布尔值、结果数量、耗时、固定枚举和异常类型；本刀不新增指标标签。
- 必须先写失败测试，再改生产 logger；不得删掉或弱化“sentinel 不得出现在日志中”的断言来迁就现状。
- 审计规则：logger 参数中的直接用户/模型 payload（包括 `query`/`keyword`、`payload` 及设计 §4.2 正文变量）全局硬拒；`Throwable`、`getMessage()`、stack 和截断正文仅在本刀修改文件内硬拒；设计 §4.2.1 的延期异常调用必须按精确 `file:line` 显式 allowlist，不能以模糊目录/通配符放行。
- STRICT 完成标准固定为“全局零直接明文 + 修改范围内零 message/stack + 延迟组明列”。延迟组不算本刀完成证据，必须在交接报告逐条原样列出并作为下一刀阻塞项。
- 评审已批准 STRICT 范围，不再保留 MINIMUM 作为本次实现分支。
- 扫描基线记录两种正则口径：本地 81/302/159，评审方独立复核 82/304/160；差异仅为跨行/别名匹配口径，验收采用 82/304/160。

---

## Task 1: 添加低敏摘要工具

**Files:**
- Create: `src/main/java/com/aseubel/yusi/common/utils/LowSensitivityLogSummary.java`
- Test: `src/test/java/com/aseubel/yusi/common/utils/LowSensitivityLogSummaryTest.java`

**Interfaces:**
- `LowSensitivityLogSummary.lengthBucket(String)` 返回且只返回 `empty`、`short`、`medium`、`long`。
- `LowSensitivityLogSummary.exceptionType(Throwable)` 返回异常类 simple name；输入为空或类名为空时返回 `unknown`。
- helper 不保存、hash、mask、持久化或记录输入。

- [ ] **Step 1: Write the failing unit test.**

先创建测试，覆盖空白、Unicode code point 边界、异常类型和输入不回显：

```java
@Test
void summarizesOnlyFixedLengthBuckets() {
    assertEquals("empty", LowSensitivityLogSummary.lengthBucket(null));
    assertEquals("empty", LowSensitivityLogSummary.lengthBucket(" \n"));
    assertEquals("short", LowSensitivityLogSummary.lengthBucket("海边🌊"));
    assertEquals("short", LowSensitivityLogSummary.lengthBucket("x".repeat(32)));
    assertEquals("medium", LowSensitivityLogSummary.lengthBucket("x".repeat(33)));
    assertEquals("medium", LowSensitivityLogSummary.lengthBucket("x".repeat(256)));
    assertEquals("long", LowSensitivityLogSummary.lengthBucket("x".repeat(257)));
    assertFalse(LowSensitivityLogSummary.lengthBucket("fixture-log-sensitive-query-7f3c")
            .contains("fixture-log-sensitive-query-7f3c"));
}

@Test
void returnsOnlyExceptionType() {
    Throwable error = new IllegalStateException("fixture-log-sensitive-query-7f3c");
    assertEquals("IllegalStateException", LowSensitivityLogSummary.exceptionType(error));
    assertEquals("unknown", LowSensitivityLogSummary.exceptionType(null));
}
```

- [ ] **Step 2: Run the focused test to prove it is red.**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=LowSensitivityLogSummaryTest" test
```

Expected: compilation failure because `LowSensitivityLogSummary` does not exist. Keep this failure as the TDD starting point.

- [ ] **Step 3: Implement the minimal helper.**

Create the following class without Lombok or framework dependencies:

```java
package com.aseubel.yusi.common.utils;

public final class LowSensitivityLogSummary {

    private LowSensitivityLogSummary() {
    }

    public static String lengthBucket(String value) {
        if (value == null || value.isBlank()) {
            return "empty";
        }
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints <= 32) {
            return "short";
        }
        if (codePoints <= 256) {
            return "medium";
        }
        return "long";
    }

    public static String exceptionType(Throwable error) {
        if (error == null || error.getClass().getSimpleName().isBlank()) {
            return "unknown";
        }
        return error.getClass().getSimpleName();
    }
}
```

- [ ] **Step 4: Run the helper test to prove it is green.**

Run the same Maven command. Expected: all helper tests pass and no test output contains the sentinel or exception message.

## Task 2: Add failing runtime log-capture tests for the four search components

**Files:**
- Create: `src/test/java/com/aseubel/yusi/security/SensitiveQueryLogSafetyTest.java`
- Read and preserve: `src/test/java/com/aseubel/yusi/service/ai/tool/DiarySearchToolTest.java`
- Read and preserve any existing tests for `LifeGraphTool` and `MidTermMemorySearchService` found with `rg --files src/test/java`.

**Interfaces:**
- Attach `ch.qos.logback.classic.spi.ILoggingEvent` `ListAppender` to each target class logger.
- Captured text includes `getFormattedMessage()` and throwable proxy class/message/stack, so passing a `Throwable` cannot evade the assertion.
- Use in-memory Mockito clients and sentinel strings only; do not use evaluation JSON, reports, H2, or external clients.

- [ ] **Step 1: Add reusable logger capture and cleanup.**

Use this structure and remove every appender in `@AfterEach`:

```java
private final List<AttachedLogger> attached = new ArrayList<>();

private ListAppender<ILoggingEvent> attach(Class<?> type) {
    ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(type);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    attached.add(new AttachedLogger(logger, appender));
    return appender;
}

private String rendered(ListAppender<ILoggingEvent> appender) {
    return appender.list.stream()
            .map(event -> event.getFormattedMessage() + " " + throwableText(event.getThrowableProxy()))
            .collect(Collectors.joining("\n"));
}

private String throwableText(IThrowableProxy proxy) {
    if (proxy == null) {
        return "";
    }
    StringBuilder text = new StringBuilder(proxy.getClassName());
    text.append(':').append(proxy.getMessage());
    for (StackTraceElementProxy frame : proxy.getStackTraceElementProxyArray()) {
        text.append(' ').append(frame.getStackTraceElement());
    }
    return text.toString();
}
```

The concrete test may use a small `record AttachedLogger(Logger logger, ListAppender<ILoggingEvent> appender)`; its `close` operation must call `logger.detachAppender(appender)` and `appender.stop()`.

- [ ] **Step 2: Write success-path sentinel cases before production changes.**

Call each search entry point using `fixture-log-sensitive-query-7f3c`, attach its logger, and assert:

```java
assertFalse(rendered(appender).contains("fixture-log-sensitive-query-7f3c"));
assertTrue(rendered(appender).contains("queryLengthBucket")
        || rendered(appender).contains("keywordLengthBucket"));
```

Also assert functional facts: `DiarySearchTool` still returns its assembled result and its log does not contain `metadata["userId"]` or concrete date strings; MCP response observers still receive the same results and completion; `LifeGraphTool` and `MidTermMemorySearchService` preserve normal return values. Do not print captured logs in assertion messages.

- [ ] **Step 3: Write exception-path sentinel cases before production changes.**

Make Milvus, Embedding, graph query, or repository boundaries throw `new IllegalStateException("fixture-log-sensitive-query-7f3c")`. Assert the existing fallback response/list contract, then assert captured events contain no sentinel and no exception message. The only exception detail allowed is `IllegalStateException` as a type field. Do not assert an exact stack trace.

- [ ] **Step 4: Run the new test and verify it fails against the current code.**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=SensitiveQueryLogSafetyTest" test
```

Expected: failure naming a leaked sentinel in one of the current query logs. Keep the failure; do not narrow the appender scope or remove error-path coverage.

## Task 3: Implement the four search log projections

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/grpc/McpGrpcServiceImpl.java:70-71,127,148,197`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/tool/DiarySearchTool.java:126-127,181,202`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/tool/LifeGraphTool.java:47,58`
- Modify: `src/main/java/com/aseubel/yusi/service/memory/MidTermMemorySearchService.java:46,102,146,161`

**Interfaces:**
- Method signatures, response observers, return values, user isolation filters, retrieval limits and fallback strings remain unchanged.
- Search variables continue unchanged into Embedding, Milvus, `LifeGraphQueryService` and repository calls.
- Logger arguments use only `LowSensitivityLogSummary`, fixed operation names, low-sensitivity IDs, counts, booleans, limits and exception types.

- [ ] **Step 1: Replace the five query-bearing `INFO` calls.**

Use these mappings, keeping the current level:

```java
// McpGrpcServiceImpl.searchDiary
log.info("MCP diary search started: userId={}, keywordLengthBucket={}, startTimePresent={}, endTimePresent={}",
        userId, LowSensitivityLogSummary.lengthBucket(keyword),
        StrUtil.isNotBlank(startTimeStr), StrUtil.isNotBlank(endTimeStr));

// McpGrpcServiceImpl.searchMemory
log.info("MCP memory search started: userId={}, queryLengthBucket={}, maxResults={}",
        userId, LowSensitivityLogSummary.lengthBucket(query), maxResults);

// DiarySearchTool.searchDiary
log.info("DiarySearchTool search started: userId={}, queryLengthBucket={}, startDatePresent={}, endDatePresent={}",
        currentUserId, LowSensitivityLogSummary.lengthBucket(query),
        StrUtil.isNotBlank(startDate), StrUtil.isNotBlank(endDate));

// LifeGraphTool.searchLifeGraph
log.info("LifeGraphTool search started: userId={}, queryLengthBucket={}",
        userId, LowSensitivityLogSummary.lengthBucket(query));

// MidTermMemorySearchService.searchMidTermMemory
log.info("MidTermMemory search started: userId={}, queryLengthBucket={}, topK={}",
        userId, LowSensitivityLogSummary.lengthBucket(query), topK);
```

- [ ] **Step 2: Replace the complete Diary `expr` debug value.**

Keep the `expr` construction and user/date filtering unchanged. Replace only its logger call with:

```java
log.debug("DiarySearchTool filter built: userId={}, dateFilterPresent={}",
        userId, StrUtil.isNotBlank(startDate) || StrUtil.isNotBlank(endDate));
```

- [ ] **Step 3: Replace target error logging without a throwable argument.**

Use the fixed operation and exception type shape:

```java
log.error("MidTermMemory search failed: userId={}, operation=search_mid_term_memory, exceptionType={}",
        userId, LowSensitivityLogSummary.exceptionType(e));
```

For MCP catches, where the authorized user variable may not be safely available, use `operation=mcp_diary_search` or `operation=mcp_memory_search` plus `exceptionType`. Do not pass `e`, `e.getMessage()`, `e.toString()` or a truncated stack. Preserve the current observer error response behavior, including its existing API response contract.

- [ ] **Step 4: Run the focused red-to-green tests.**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=SensitiveQueryLogSafetyTest,DiarySearchToolTest" test
```

Expected: sentinel log assertions and existing Diary retrieval assertions pass; no external service starts.

## Task 4: Resolve the full-scan scope gate

**Files:**
- Modify the 11 adjacent production files listed in Step 3 below under the approved STRICT scope.
- Modify audit-discovered `src/main/java/com/aseubel/yusi/redis/aspect/SpelResolverAspect.java:55-61` with the same SpEL low-sensitivity projection.
- No roadmap edit.

**Interfaces:**
- STRICT has one completion rule: `src/main` contains no reviewed logger call that emits direct user/model payload; the files modified in this slice contain no exception message/stack; and every deferred exception is represented by the exact §4.2.1 allowlist.

- [ ] **Step 1: Record the approved STRICT adjacent-hit decision.**

Use design section 4.2 and the approved decision before production edits:

```text
STRICT: include confirmed direct payload logs in this slice and require global low-sensitivity audit.
```

Do not weaken the sentinel assertions or replace STRICT with a local four-file scan.

- [ ] **Step 2: Write failing appender cases for strict-scope payloads.**

Create `src/test/java/com/aseubel/yusi/security/SensitivePayloadLogSafetyTest.java`. Use in-memory sentinels for persona fields, situation report output, conflict description/reason, location name, device info, image JSON and SpEL values. Attach loggers and assert neither formatted messages nor throwable proxies contain the sentinels. Also assert a fixed status/count field exists so deleting logs cannot pass accidentally. Run:

```powershell
.\mvnw.cmd -q "-Dtest=SensitivePayloadLogSafetyTest" test
```

Expected: the current direct payload logs fail. Keep the failure and do not weaken any sentinel assertion.

- [ ] **Step 3: Under STRICT, replace confirmed direct payload logger arguments.**

Modify only these confirmed direct-payload sites and preserve business values and fallback behavior:

| File and line | Replacement |
| --- | --- |
| `UserPersonaTool.java:58-59` | user ID, nonblank updated-field count, fixed operation |
| `SituationReportService.java:59,73,104-111` | scenario ID, output status/length bucket, fixed invalid-format category and exception type |
| `CognitiveConflictDetector.java:111` | user ID and `conflictDetected=true` |
| `MidMemoryFusionService.java:165-166` | low-sensitivity IDs and fixed merge/conflict action, no LLM reason |
| `MatchServiceImpl.java:229` | `userAId`/`userBId`, no display names |
| `UserLocationServiceImpl.java:48` | user ID and fixed location operation/field count, no name |
| `TokenServiceImpl.java:97` | user ID and `deviceInfoPresent`, no device string |
| `DiaryServiceImpl.java:358` | fixed invalid-image-payload category and exception type, no JSON |
| `PersistentChatMemoryStore.java:310` | fixed image-parse category and exception type, no JSON |
| `SpelResolverHelper.java:47-53` | fixed resolution status/type, no expression/value/message |
| `SpelResolverAspect.java:55-61` | fixed resolution status/type, no expression/value/throwable |
| `EmotionAnalyzerImpl.java:65` | normalized fixed emotion enum or `UNKNOWN`, no raw model output |

For every error/warn logger in these 11 files and the audit-discovered SpEL aspect, do not pass a `Throwable`, `getMessage()`, stack or truncated output; use `LowSensitivityLogSummary.exceptionType(exception)` with a fixed operation/category and keep the existing returned fallback. This includes existing adjacent error logs at `CognitiveConflictDetector:114`, `MidMemoryFusionService:70/76/172`, `MatchServiceImpl:279/320/422/702/981`, `DiaryServiceImpl:197`, `PersistentChatMemoryStore:67/199`, `SpelResolverHelper:53`, `SpelResolverAspect:61`, and `EmotionAnalyzerImpl:69`.

- [ ] **Step 4: Run strict-scope affected tests.**

First discover existing names:

```powershell
rg --files src/test/java | rg "(UserPersonaTool|SituationReport|CognitiveConflict|MidMemoryFusion|MatchService|UserLocation|TokenService|DiaryService|PersistentChatMemoryStore|SpelResolver|EmotionAnalyzer).*Test\.java$"
```

Run every existing matching test plus the new safety test in one command. Do not add nonexistent class names to Surefire. Expected: all available affected functional tests and `SensitivePayloadLogSafetyTest` pass.

## Task 5: Add the source audit and verify the implementation slice

**Files:**
- Create: `src/test/java/com/aseubel/yusi/security/SensitiveLogSourceAuditTest.java`
- No fixture/report/roadmap changes.

**Interfaces:**
- The audit scans all `src/main/java/**/*.java` logger invocation blocks, including multiline calls.
- Direct payload arguments are globally rejected; `Throwable`/`getMessage()`/stack/truncated output are rejected in every file modified by this slice.
- The modified logger file set is the 15-file STRICT set plus `SpelResolverAspect.java`, which was discovered by the full scan and must satisfy the same message/stack rule.
- The deferred exception policy group is an explicit exact allowlist: `GlobalExceptionHandler.java:86`, `GlobalExceptionHandler.java:89`, `ModelProxyFactory.java:279`, `PromptManager.java:64`, `PromptManager.java:123`, `AdminServiceImpl.java:302`, `CacheAspect.java:267`, each with the §4.2.1 reason. No directory or wildcard allowance is valid.
- It fails with a fixed location/code, never echoing source values or sentinel text.
- It records explicit safe categories for remaining logger arguments: counts, fixed enums, model IDs, low-sensitivity resource IDs, and exception type.

- [ ] **Step 1: Write the source audit before final cleanup.**

Implement a test that reads every Java source file, joins logger call lines through the closing `);`, and rejects a logger block that directly emits `query`, `keyword`, raw content/output fields, known user payload fields, `Throwable`, `getMessage()`, or truncated model/result text. The test must not use a single-line regex, and it must not scan only the four target files.

- [ ] **Step 2: Run the source audit and fix the reported logger projection.**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=SensitiveLogSourceAuditTest" test
```

Expected under STRICT: PASS with zero direct payload logger blocks, zero message/stack blocks in modified files, and exactly the seven deferred locations represented by the explicit allowlist. Any unlisted direct payload or modified-file message/stack is a failure.

- [ ] **Step 3: Run focused verification.**

```powershell
.\mvnw.cmd -q "-Dtest=LowSensitivityLogSummaryTest,SensitiveQueryLogSafetyTest,SensitivePayloadLogSafetyTest,SensitiveLogSourceAuditTest,DiarySearchToolTest,PersistentChatMemoryStoreCorrelationTest" test
```

If `SensitivePayloadLogSafetyTest` is excluded under the approved minimum scope, replace it with the existing affected tests and retain the source-audit failure as a documented blocker. Expected strict result: all selected tests pass and no captured log contains a sentinel.

- [ ] **Step 4: Run full Maven and scope checks.**

```powershell
.\mvnw.cmd -q test
git diff --check
git status --short
git diff --name-only
rg -n -i "\blog\.(trace|debug|info|warn|error)\s*\(" src/main/java --glob "*.java"
rg -n -i "\b(query|keyword|plainContent|profileText|reason|description|jsonReport|deviceInfo|imagesJson|preferredName|customInstructions)\b" src/main/java --glob "*.java"
```

Expected strict result: Maven exits `0`, `git diff --check` is clean, every remaining grep hit is explicitly classified by the audit or the exact deferred allowlist, and no evaluation/roadmap/backlog file is changed.

- [ ] **Step 5: Check roadmap status before any commit.**

Confirm with a read-only diff that `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md` is unchanged. Per repository instructions, do not tick its checkbox in this slice; report whether the strict evidence is sufficient for a later reviewer decision.

- [ ] **Step 6: Commit only after reviewer approval and evidence.**

For strict scope, stage only the helper, approved production log projections and their tests, then commit:

```powershell
git add docs/engineering/specs/2026-08-19-yusi-sensitive-log-convergence-design.md docs/engineering/plans/2026-08-19-yusi-sensitive-log-convergence-implementation-plan.md src/main/java/com/aseubel/yusi/common/utils/LowSensitivityLogSummary.java src/main/java/com/aseubel/yusi/grpc/McpGrpcServiceImpl.java src/main/java/com/aseubel/yusi/service/ai/tool/DiarySearchTool.java src/main/java/com/aseubel/yusi/service/ai/tool/LifeGraphTool.java src/main/java/com/aseubel/yusi/service/memory/MidTermMemorySearchService.java src/main/java/com/aseubel/yusi/service/ai/tool/UserPersonaTool.java src/main/java/com/aseubel/yusi/service/room/impl/SituationReportService.java src/main/java/com/aseubel/yusi/service/cognition/CognitiveConflictDetector.java src/main/java/com/aseubel/yusi/service/cognition/MidMemoryFusionService.java src/main/java/com/aseubel/yusi/service/match/impl/MatchServiceImpl.java src/main/java/com/aseubel/yusi/service/location/impl/UserLocationServiceImpl.java src/main/java/com/aseubel/yusi/service/user/impl/TokenServiceImpl.java src/main/java/com/aseubel/yusi/service/diary/impl/DiaryServiceImpl.java src/main/java/com/aseubel/yusi/config/ai/PersistentChatMemoryStore.java src/main/java/com/aseubel/yusi/common/utils/SpelResolverHelper.java src/main/java/com/aseubel/yusi/service/plaza/impl/EmotionAnalyzerImpl.java src/main/java/com/aseubel/yusi/redis/aspect/SpelResolverAspect.java src/test/java/com/aseubel/yusi/common/utils/LowSensitivityLogSummaryTest.java src/test/java/com/aseubel/yusi/security/SensitiveQueryLogSafetyTest.java src/test/java/com/aseubel/yusi/security/SensitivePayloadLogSafetyTest.java src/test/java/com/aseubel/yusi/security/SensitiveLogSourceAuditTest.java
git commit -m "security: converge sensitive production logs"
```

Expected: one independent implementation commit, no roadmap edit, and a handoff containing focused/full test exit codes, source-audit result, remaining grep classifications, and commit hash. Under minimum scope, do not commit a false completion; stop with the blocker list for review.

## Plan self-review

- Spec coverage: exact four query components, five query log calls, error paths, complete Diary `expr`, full scan, adjacent direct payloads, low-sensitivity strategy, replacement diagnostics, runtime capture, functional regression, Maven verification, diff scope, roadmap check and Phase 5 boundaries are mapped above.
- Completeness scan: every production file, test file, helper API, command and expected result is named. The only conditional branch is the explicit reviewer scope decision, with a defined non-completion outcome.
- Type consistency: Task 1 defines the helper methods used by Tasks 3-5; Task 2 defines `ListAppender` capture and Task 5 reuses its semantics without relying on evaluation report ordering.
- Boundary check: no task edits Phase 4 reports/fixtures, adds observability, runs backups, changes CI or starts a service.
