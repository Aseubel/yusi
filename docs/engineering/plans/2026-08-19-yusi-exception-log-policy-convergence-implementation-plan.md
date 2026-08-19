# Yusi 异常日志政策收敛实施计划

> **执行纪律：** 本仓库禁止子 agent 和 auto-review；按本计划在当前会话 inline 执行。每个步骤都以 checkbox 跟踪，并在生产改动前保留失败证据。

**目标：** 收敛上一刀 7 个延期异常日志位置及其 5 个归属类的同类日志，使延期 allowlist 为空且默认日志不再输出异常 message、Throwable、SQL 或 cache key。

**架构：** 复用 LowSensitivityLogSummary.exceptionType/lengthBucket，以固定 operation、failure category、低敏 ID、模型路由元数据、statementIndex 和 key 长度桶替换 raw exception projection。扩展 SensitiveLogSourceAuditTest，将 5 个归属类纳入异常日志静态门槛，并将 DEFERRED_EXCEPTIONS 置空；运行时使用 Logback ListAppender 检查异常 message/key/SQL sentinel 不进入日志。

**技术栈：** Java 21、Spring Boot 3.4.5、SLF4J/Logback、JUnit 5、Mockito、Maven Wrapper。

## 全局约束

- 不启动应用服务、H2/Milvus/Redis/MySQL/OSS 或任何外部依赖；只运行 Maven 测试命令。
- 不修改 roadmap、CI、migration、Phase 4 评测 fixture/loader/report、QualityGatePolicy、post-release backlog 或指标/Trace 注入。
- 生产允许修改文件严格限定为 GlobalExceptionHandler.java、ModelProxyFactory.java、PromptManager.java、AdminServiceImpl.java、CacheAspect.java。
- 日志不得传 Throwable、getMessage()、cause message、stack、截断正文、SQL、cache key、Prompt 正文、模型响应或请求正文。
- 允许日志字段仅为异常类型、固定 operation/category、低敏 ID、模型 provider/modelId、attempt、statementIndex、key 长度桶、布尔值和数量。
- 测试 sentinel 只能使用 fixture-* 脱敏 ID；不写自然语言正文，不创建评测 fixture/report。
- 先红后绿；不得删除或弱化 sentinel、无 Throwable 和 allowlist-empty 断言。
- 现有客户端响应、模型 fallback、Prompt fallback、管理员清理顺序、Cache 锁释放/原异常重抛行为必须保持不变。

---

## Task 1: 建立异常政策红线与运行时 sentinel 测试

**文件：**

- Modify: src/test/java/com/aseubel/yusi/security/SensitiveLogSourceAuditTest.java
- Create: src/test/java/com/aseubel/yusi/security/SensitiveExceptionLogSafetyTest.java
- No production file in this task

**接口：**

- Reuse: LowSensitivityLogSummary.exceptionType(Throwable) and lengthBucket(String).
- Produce: EXCEPTION_POLICY_LOG_FILES containing the five owner classes, empty DEFERRED_EXCEPTIONS, and runtime sentinel coverage for all five classes.

- [ ] **Step 1: Expand the static audit scope and empty the deferred list.**

In SensitiveLogSourceAuditTest, add these exact paths:

~~~java
private static final Set<String> EXCEPTION_POLICY_LOG_FILES = Set.of(
        "src/main/java/com/aseubel/yusi/common/exception/GlobalExceptionHandler.java",
        "src/main/java/com/aseubel/yusi/service/ai/model/ModelProxyFactory.java",
        "src/main/java/com/aseubel/yusi/service/ai/prompt/PromptManager.java",
        "src/main/java/com/aseubel/yusi/service/user/impl/AdminServiceImpl.java",
        "src/main/java/com/aseubel/yusi/redis/aspect/CacheAspect.java");

private static final List<DeferredException> DEFERRED_EXCEPTIONS = List.of();
~~~

Keep the exact-location record and make the empty-list assertion explicit. Change the direct payload branch to reject a match without checking a deferred flag; a future deferred entry must never bypass the global direct payload rule. Apply the modified-file message/stack assertion to EXCEPTION_POLICY_LOG_FILES as well as the previous strict set.

- [ ] **Step 2: Write runtime sentinel tests before production changes.**

Create SensitiveExceptionLogSafetyTest with Logback ListAppender capture and these methods:

~~~java
void globalHandlerDoesNotLogSseExceptionMessageOrThrowable()
void globalHandlerDoesNotLogUnhandledExceptionMessageOrThrowable()
void modelProxyDoesNotLogProviderErrorMessage()
void promptManagerDoesNotLogDatabaseOrClasspathExceptionMessage()
void adminCleanupDoesNotLogSqlOrExceptionMessage()
void cacheAspectDoesNotLogKeyOrExceptionMessage()
~~~

Use fixture-exception-message-7f3c, fixture-model-error-7f3c, fixture-prompt-error-7f3c, fixture-admin-error-7f3c, fixture-cache-key-7f3c, and fixture-cache-error-7f3c. Render formatted messages and IThrowableProxy; assert each sentinel is absent and each captured event has no throwable proxy. Configure mocks so each original fallback/cleanup/rethrow path still executes.

- [ ] **Step 3: Run the new red tests.**

Run: .\mvnw.cmd -q "-Dtest=SensitiveExceptionLogSafetyTest,SensitiveLogSourceAuditTest" test

Expected: non-zero. The source audit reports the non-empty deferred group or policy-file message/stack blocks, and runtime tests observe at least one old exception message/key/Throwable sentinel. Do not alter assertions to accept old output.

---

## Task 2: Converge GlobalExceptionHandler

**文件：**

- Modify: src/main/java/com/aseubel/yusi/common/exception/GlobalExceptionHandler.java:83-92
- Test: src/test/java/com/aseubel/yusi/security/SensitiveExceptionLogSafetyTest.java

**接口：**

- Consume: LowSensitivityLogSummary.exceptionType(Throwable).
- Preserve: streaming null return, non-streaming status 500, and existing Response body behavior.

- [ ] **Step 1: Replace the SSE-after-commit logger.**

~~~java
log.debug("Global exception after streaming response: operation=sse_after_commit, exceptionType={}",
        LowSensitivityLogSummary.exceptionType(e));
~~~

- [ ] **Step 2: Replace the generic 500 logger.**

~~~java
log.error("Unhandled HTTP exception: operation=global_unhandled_exception, status=500, exceptionType={}",
        LowSensitivityLogSummary.exceptionType(e));
~~~

Keep ERROR and the existing status/response code. Do not change Response.fail("系统内部错误: " + e.getMessage()) in this slice; the design records it as a separate client-response boundary.

- [ ] **Step 3: Run the handler sentinel tests.**

Run: .\mvnw.cmd -q "-Dtest=SensitiveExceptionLogSafetyTest" test

Expected: the two GlobalExceptionHandler tests pass; the remaining policy tests may stay red until later tasks.

---

## Task 3: Converge model and Prompt exception projections

**文件：**

- Modify: src/main/java/com/aseubel/yusi/service/ai/model/ModelProxyFactory.java:279,437
- Modify: src/main/java/com/aseubel/yusi/service/ai/prompt/PromptManager.java:64,94,123
- Test: src/test/java/com/aseubel/yusi/security/SensitiveExceptionLogSafetyTest.java
- Regression: src/test/java/com/aseubel/yusi/service/ai/model/ModelProxyFactoryTest.java
- Regression: src/test/java/com/aseubel/yusi/service/ai/prompt/PromptManagerTest.java

**接口：**

- Model fields: attempt, requestId, runId, provider, modelId, failureKind, fallbackEligible, root exception type.
- Prompt fields: operation, bounded prompt key, exception type.
- Preserve ModelInvocationErrorClassifier message-based classification as internal logic; do not pass it to a logger.

- [ ] **Step 1: Replace the model invocation error message.**

Use existing ModelRouteContext, ModelInstance, and normalized failure kind. Never pass normalized or normalized.getMessage():

~~~java
log.warn("AI model invocation failed: operation=model_invoke, attempt={}, requestId={}, runId={}, provider={}, modelId={}, failureKind={}, fallbackEligible={}, exceptionType={}",
        attemptIndex + 1,
        context.getRequestId(),
        context.getRunId(),
        selected.getProvider(),
        selected.getId(),
        normalized.kind().name(),
        normalized.isFallbackEligible(false),
        LowSensitivityLogSummary.exceptionType(normalized.getCause()));
~~~

- [ ] **Step 2: Replace model attempt-event publish failure logging.**

~~~java
log.warn("Model attempt event publish failed: operation=publish_model_attempt, attemptId={}, exceptionType={}",
        event.attemptId(), LowSensitivityLogSummary.exceptionType(publishFailure));
~~~

- [ ] **Step 3: Replace all PromptManager exception projections.**

~~~java
log.warn("Prompt database load failed: operation=prompt_load_db, promptKey={}, exceptionType={}",
        keyStr, LowSensitivityLogSummary.exceptionType(e));
log.warn("Prompt classpath load failed: operation=prompt_load_classpath, promptKey={}, exceptionType={}",
        keyStr, LowSensitivityLogSummary.exceptionType(e));
log.debug("Prompt auto-initialization skipped: operation=prompt_auto_init, promptKey={}, exceptionType={}",
        keyStr, LowSensitivityLogSummary.exceptionType(e));
~~~

Preserve DB -> classpath -> hardcoded fallback, cache and auto-init behavior.

- [ ] **Step 4: Run model/Prompt regressions and sentinel tests.**

Run: .\mvnw.cmd -q "-Dtest=SensitiveExceptionLogSafetyTest,ModelProxyFactoryTest,PromptManagerTest" test

Expected: selected tests pass; model fallback/event and Prompt snapshot/fallback assertions remain unchanged.

---

## Task 4: Converge Admin and Cache exception projections

**文件：**

- Modify: src/main/java/com/aseubel/yusi/service/user/impl/AdminServiceImpl.java:213,220,230,257,302
- Modify: src/main/java/com/aseubel/yusi/redis/aspect/CacheAspect.java:198,224,267,273
- Test: src/test/java/com/aseubel/yusi/security/SensitiveExceptionLogSafetyTest.java
- Regression: src/test/java/com/aseubel/yusi/service/user/AdminServiceAuditTest.java

**接口：**

- Admin fields: operation, low-sensitivity userId, statementIndex, exception type.
- Cache fields: operation, LowSensitivityLogSummary.lengthBucket(key), exception type.
- Preserve Admin cleanup continuation/audit and Cache lock release/async behavior/rethrow.

- [ ] **Step 1: Replace all Admin cleanup message/SQL projections.**

Change the delete query loop to an indexed loop; SQL values and parameter order remain unchanged:

~~~java
for (int statementIndex = 0; statementIndex < deleteQueries.length; statementIndex++) {
    String query = deleteQueries[statementIndex];
    try {
        if (query.contains("OR")) {
            jdbcTemplate.update(query, userId, userId);
        } else {
            jdbcTemplate.update(query, userId);
        }
    } catch (Exception e) {
        log.error("Admin deregistration cleanup failed: operation=delete_related_data, userId={}, statementIndex={}, exceptionType={}",
                userId, statementIndex, LowSensitivityLogSummary.exceptionType(e));
    }
}
~~~

Convert the four fixed cleanup catches to operation + userId + exceptionType. Keep transaction boundaries and the final admin audit event.

- [ ] **Step 2: Replace all Cache key/Throwable projections.**

~~~java
log.warn("Cache lock wait timed out: operation=cache_lock_timeout, keyLengthBucket={}",
        LowSensitivityLogSummary.lengthBucket(key));
log.error("Cache async refresh failed: operation=cache_async_refresh, keyLengthBucket={}, exceptionType={}",
        LowSensitivityLogSummary.lengthBucket(key), LowSensitivityLogSummary.exceptionType(e));
log.error("Cache query data failed: operation=cache_query_data, keyLengthBucket={}, exceptionType={}",
        LowSensitivityLogSummary.lengthBucket(key), LowSensitivityLogSummary.exceptionType(e));
log.warn("Cache lock release failed: operation=cache_release_lock, keyLengthBucket={}, exceptionType={}",
        LowSensitivityLogSummary.lengthBucket(key), LowSensitivityLogSummary.exceptionType(ex));
~~~

- [ ] **Step 3: Run Admin/Cache sentinel and regression tests.**

Run: .\mvnw.cmd -q "-Dtest=SensitiveExceptionLogSafetyTest,AdminServiceAuditTest" test

Expected: selected tests pass; admin cleanup continues, cache queryData releases its lock and rethrows, and raw key/exception sentinels are absent.

---

## Task 5: Make the audit prove allowlist closure

**文件：**

- Modify: src/test/java/com/aseubel/yusi/security/SensitiveLogSourceAuditTest.java
- Test: all existing security tests plus SensitiveExceptionLogSafetyTest

- [ ] **Step 1: Assert the deferred group is empty.**

~~~java
assertTrue(DEFERRED_EXCEPTIONS.isEmpty(),
        "SECURITY_LOG_DEFERRED_ALLOWLIST_NOT_EMPTY");
assertEquals(Set.of(), observedDeferred,
        "SECURITY_LOG_DEFERRED_LOCATIONS_OBSERVED");
~~~

Retain exact-location code and reason fields so a future reintroduction is visible, but do not add a new location to make a failing test pass.

- [ ] **Step 2: Reject all exception message/stack logger blocks in the five policy files.**

Join multiline logger calls, strip only string literals and the two approved helper calls, then assert zero findings for getMessage(), Throwable, printStackTrace, getStackTrace, throwable logger arguments, and truncated raw exception/model output in EXCEPTION_POLICY_LOG_FILES. Keep the previous global direct payload check active.

- [ ] **Step 3: Run the focused gate.**

Run: .\mvnw.cmd -q "-Dtest=LowSensitivityLogSummaryTest,SensitiveQueryLogSafetyTest,SensitivePayloadLogSafetyTest,SensitiveExceptionLogSafetyTest,SensitiveLogSourceAuditTest,PromptManagerTest,ModelProxyFactoryTest,AdminServiceAuditTest" test

Expected: exit code 0; direct payload findings, policy-file message/stack findings, observed deferred locations and deferred list are all empty.

- [ ] **Step 4: Run full regression and scope checks.**

Run these commands in order: .\mvnw.cmd -q test; git diff --check; git status --short; git diff --name-only; rg -n -i "\\b(?:log|logger)\\.(?:trace|debug|info|warn|error)\\s*\\(" src/main/java --glob "*.java"

Expected: Maven exits 0, diff check is clean, no evaluation/roadmap/backlog/CI/migration file is changed, and production changes are limited to the five policy classes.

- [ ] **Step 5: Check roadmap before commit.**

Read-only confirm docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md is unchanged. Do not tick any checkbox; report allowlist closure as evidence for reviewer decision only.

---

## Task 6: Commit after review approval

**文件：**

- Design: docs/engineering/specs/2026-08-19-yusi-exception-log-policy-convergence-design.md
- Plan: docs/engineering/plans/2026-08-19-yusi-exception-log-policy-convergence-implementation-plan.md
- Production: the five policy classes only
- Tests: SensitiveExceptionLogSafetyTest.java, SensitiveLogSourceAuditTest.java, and directly affected regression test edits

- [ ] **Step 1: Stage only the approved exception-policy slice.**

The staging check must contain the two docs, exactly the five policy production files, the new exception safety test, the evolved source audit, and approved regression test changes. It must not contain roadmap, evaluation, fixture, report, CI, migration, observability, or backup files.

- [ ] **Step 2: Commit the slice.**

~~~powershell
git add docs/engineering/specs/2026-08-19-yusi-exception-log-policy-convergence-design.md docs/engineering/plans/2026-08-19-yusi-exception-log-policy-convergence-implementation-plan.md src/main/java/com/aseubel/yusi/common/exception/GlobalExceptionHandler.java src/main/java/com/aseubel/yusi/service/ai/model/ModelProxyFactory.java src/main/java/com/aseubel/yusi/service/ai/prompt/PromptManager.java src/main/java/com/aseubel/yusi/service/user/impl/AdminServiceImpl.java src/main/java/com/aseubel/yusi/redis/aspect/CacheAspect.java src/test/java/com/aseubel/yusi/security/SensitiveExceptionLogSafetyTest.java src/test/java/com/aseubel/yusi/security/SensitiveLogSourceAuditTest.java src/test/java/com/aseubel/yusi/service/ai/model/ModelProxyFactoryTest.java src/test/java/com/aseubel/yusi/service/ai/prompt/PromptManagerTest.java src/test/java/com/aseubel/yusi/service/user/AdminServiceAuditTest.java
git commit -m "security: close deferred exception log policy"
~~~

Expected: one independent commit, empty deferred allowlist, clean working tree, no roadmap change. Stop and report focused/full exit codes, audit zero counts, the five policy file set, residual out-of-scope logger classifications, and the commit hash.

## Plan self-review

- **Spec coverage:** all 7 original locations have context, risk, consumer, policy, and tests; all 9 same-class companion logger hits are covered; GlobalExceptionHandler stack tradeoff and controlled-channel boundary are explicit.
- **Audit coverage:** direct payload scan remains global; the five exception-policy classes are scanned for all multiline logger message/stack projections; deferred list must be empty.
- **Behavior coverage:** handler response/streaming behavior, model fallback/event behavior, Prompt fallback/cache behavior, Admin cleanup continuation/audit, and Cache lock/rethrow behavior each have regression paths.
- **Scope coverage:** no evaluation/report/fixture/roadmap/CI/migration/metrics/Trace/backup changes are planned.
- **Placeholder scan:** no TBD, TODO, wildcard allowlist, or unspecified test command is used.
