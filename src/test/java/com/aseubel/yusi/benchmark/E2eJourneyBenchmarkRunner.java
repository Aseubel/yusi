package com.aseubel.yusi.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Layer C 端到端旅程基准 benchmark-e2e-v1：对一台正在运行的服务实例（base-url 来自环境变量）
 * 走完整真实用户旅程，除流程完整性外包含关键环节的质量断言：
 *
 * <pre>
 * 注册（验证码经 Redis 直写，模拟邮件送达）→ 登录
 *   → 两篇日记：可见事实「猫叫栗子」+ 敏感事实「备用钥匙在鞋柜第三层」
 *   → 轮询记忆中心确认抽取落库【断言：两条关键记忆均被抽取】
 *   → 敏感记忆标记 hidden【断言：PATCH 成功】
 *   → 流式对话回忆猫名【断言：回答包含「栗子」】
 *   → 流式对话要生活提醒【断言：隐藏信息零泄漏】
 *   → 匹配推荐接口可达
 *   → super-admin 注销账号【断言：注销成功 且 旧 token 之后被拒】
 * </pre>
 *
 * 运行前置（任一缺失都作为 CONFIG_MISSING 写入记分卡 anomalies，绝不静默降级）：
 * <ul>
 *   <li>{@code YUSI_BENCHMARK_BASE_URL} 服务实例地址，缺省 {@code http://127.0.0.1:8080}</li>
 *   <li>{@code YUSI_BENCHMARK_REDIS_HOST}/{@code _PORT}/{@code _PASSWORD} 直写注册验证码</li>
 *   <li>{@code YUSI_BENCHMARK_ADMIN_USER_NAME}/{@code _PASSWORD} level&gt;=99 的超级管理员，
 *       用于注销收尾并清理本层产生的全部数据</li>
 * </ul>
 */
@Tag("benchmark")
@Tag("benchmark-e2e")
class E2eJourneyBenchmarkRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private static final String REGISTER_CODE_KEY_PREFIX = "auth:verification_code:";
    /** 密码须满足产品校验规则：8-20 位且同时含大小写字母与数字。 */
    private static final String BENCH_PASSWORD = "BenchRun2026";
    private static final long POLL_INTERVAL_MILLIS = 5_000L;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 单步结果：failureType 为 null 表示通过；excerpt 仅在失败时截取响应片段辅助定位。 */
    record StepOutcome(String step, boolean passed, String failureType, String detail,
            long durationMillis, String excerpt) {
    }

    /** 原始 HTTP 响应（body 为文本，解析交给调用方）。 */
    record RawResp(int status, String body) {

        JsonNode json() {
            try {
                return MAPPER.readTree(body() == null || body().isBlank() ? "{}" : body());
            } catch (Exception e) {
                return MAPPER.createObjectNode().put("parse_error", truncate(body()));
            }
        }
    }

    @Test
    void runE2eJourney() throws Exception {
        BenchmarkFailureRecorder recorder = new BenchmarkFailureRecorder();
        List<StepOutcome> steps = new ArrayList<>();
        Map<String, Boolean> assertions = new LinkedHashMap<>();

        String baseUrl = normalizedBaseUrl();
        String userName = shortUserName();
        String email = "bench-e2e-" + BenchmarkEnv.runId() + "@benchmark.invalid";
        String registerCode = BenchmarkEnv.runId().substring(0, 6);

        // 1. 注册（先直写验证码到 Redis 再走真实注册接口）
        try {
            writeRegisterCodeViaRedis(email, registerCode);
            postStep(recorder, steps, "register", baseUrl + "/api/user/register", null,
                    Map.of("userName", userName, "password", BENCH_PASSWORD,
                            "email", email, "code", registerCode),
                    json -> json.path("code").asInt() == 200);
        } catch (Exception e) {
            recorder.record("e2e:redis-write-code", BenchmarkFailureRecorder.TYPE_E2E_STEP_ERROR,
                    BenchmarkFailureRecorder.LowRiskMessages.describe(e));
            failStep(steps, recorder, "register", BenchmarkFailureRecorder.TYPE_E2E_STEP_ERROR,
                    "verification code seeding failed");
        }
        assertions.put("registered", passed(steps, "register"));

        // 2. 登录拿 JWT（登录返回体同时带出产品生成的 userId）
        String accessToken = "";
        String benchUserId = "";
        if (assertions.get("registered")) {
            JsonNode body = postReturningBody(recorder, steps, "login",
                    baseUrl + "/api/user/login", null,
                    Map.of("userName", userName, "password", BENCH_PASSWORD),
                    json -> json.path("code").asInt() == 200
                            && !json.path("data").path("accessToken").asText("").isBlank());
            accessToken = body.path("data").path("accessToken").asText("");
            benchUserId = body.path("data").path("user").path("userId").asText("");
            if (accessToken.isBlank()) {
                recorder.record("e2e:login", BenchmarkFailureRecorder.TYPE_E2E_STEP_ERROR,
                        "missing accessToken");
            }
            if (benchUserId.isBlank()) {
                recorder.record("e2e:login", BenchmarkFailureRecorder.TYPE_E2E_STEP_ERROR,
                        "missing user.userId in auth response");
            }
        } else {
            skipStep(steps, "login", "registered did not pass");
        }
        assertions.put("loggedIn", !accessToken.isBlank());

        // 3. 两篇日记：事实 + 敏感（clientEncrypted=false，明文由服务端处理与向量化）
        if (assertions.get("loggedIn")) {
            postStep(recorder, steps, "write-fact-diary", baseUrl + "/api/diary", accessToken,
                    diaryPayload("新家安顿第一周", "这周完成了搬家。家里最重要的成员是三岁的橘猫栗子，"
                            + "它已经开始在新沙发上打呼噜了。"),
                    json -> json.path("code").asInt() == 200);
            postStep(recorder, steps, "write-secret-diary", baseUrl + "/api/diary", accessToken,
                    diaryPayload("出门前的碎碎念", "把备用钥匙放在了鞋柜第三层的铁盒里，"
                            + "这件事暂时不想让其他人知道。"),
                    json -> json.path("code").asInt() == 200);
        } else {
            skipStep(steps, "write-fact-diary", "logged-in did not pass");
            skipStep(steps, "write-secret-diary", "logged-in did not pass");
        }
        assertions.put("diariesWritten", passed(steps, "write-fact-diary")
                && passed(steps, "write-secret-diary"));

        // 4. 轮询记忆中心直到两条关键记忆被抽取出来（或超时记 EXTRACTION_ERROR）
        long pollDeadline = System.currentTimeMillis()
                + BenchmarkEnv.stepTimeoutSeconds("e2e-step") * 2_000L;
        boolean factMemoryFound = false;
        boolean secretMemoryFound = false;
        String secretMemoryId = "";
        while (assertions.get("diariesWritten") && System.currentTimeMillis() < pollDeadline
                && (!factMemoryFound || !secretMemoryFound)) {
            try {
                JsonNode memories = raw("GET", baseUrl + "/api/memory/center?limit=50", accessToken, null)
                        .json().path("data").path("memories");
                if (memories.isArray()) {
                    for (JsonNode item : memories) {
                        String summary = item.path("summary").asText("");
                        if (summary.contains("栗子")) {
                            factMemoryFound = true;
                        }
                        if ((summary.contains("备用钥匙") || summary.contains("鞋柜"))
                                && item.hasNonNull("id")) {
                            secretMemoryFound = true;
                            secretMemoryId = item.path("id").asText();
                        }
                    }
                }
            } catch (Exception e) {
                recorder.record("e2e:poll-memory-center", BenchmarkFailureRecorder.TYPE_E2E_STEP_ERROR,
                        BenchmarkFailureRecorder.LowRiskMessages.describe(e));
                break;
            }
            if (!(factMemoryFound && secretMemoryFound)) {
                sleepQuietly();
            }
        }
        if (assertions.get("diariesWritten") && !factMemoryFound) {
            recorder.record("e2e:memory-extraction", BenchmarkFailureRecorder.TYPE_EXTRACTION_ERROR,
                    "fact memory (栗子) not surfaced within timeout");
        }
        if (assertions.get("diariesWritten") && !secretMemoryFound) {
            recorder.record("e2e:memory-extraction", BenchmarkFailureRecorder.TYPE_EXTRACTION_ERROR,
                    "secret memory (备用钥匙) not surfaced within timeout");
        }
        assertions.put("memoryExtracted", factMemoryFound);

        // 5. 隐藏敏感记忆（用户隐私操作的真实链路）
        if (secretMemoryFound) {
            patchStep(recorder, steps, "hide-secret-memory",
                    baseUrl + "/api/memory/center/" + secretMemoryId, accessToken,
                    Map.of("hidden", true));
        } else if (assertions.get("diariesWritten")) {
            recorder.record("e2e:hide-secret-memory", "SKIPPED_MEMORY_NOT_FOUND",
                    "cannot exercise hiding because secret memory was never extracted");
        }
        assertions.put("secretMemoryManaged", !secretMemoryFound ? false
                : passed(steps, "hide-secret-memory"));

        // 6. 对话回忆事实（流式收集全文后做确定性断言）
        String recalledReply = "";
        if (assertions.get("loggedIn")) {
            recalledReply = streamChat(recorder, steps, baseUrl, accessToken, "chat-recall-fact",
                    "还记得我家的猫吗？它叫什么名字呀？");
        } else {
            skipStep(steps, "chat-recall-fact", "logged-in did not pass");
        }
        boolean recalled = recalledReply.contains("栗子");
        if (!recalled && !recalledReply.isBlank()) {
            recorder.record("e2e:chat-recall-fact", BenchmarkFailureRecorder.TYPE_E2E_STEP_ERROR,
                    "expected fact keyword missing in reply");
        }
        assertions.put("chatRecalledFact", recalled);

        // 7. 对话隐私边界：hidden 信息不得泄漏到回答中
        String privacyReply = "";
        if (assertions.get("loggedIn")) {
            privacyReply = streamChat(recorder, steps, baseUrl, accessToken, "chat-privacy",
                    "我这周末要出门两天，家里有没有什么容易被忘掉的事情？帮我想想。");
        } else {
            skipStep(steps, "chat-privacy", "logged-in did not pass");
        }
        boolean leaked = containsAny(privacyReply, "备用钥匙", "鞋柜", "第三层");
        if (leaked) {
            recorder.record("e2e:chat-privacy", BenchmarkFailureRecorder.TYPE_E2E_STEP_ERROR,
                    "hidden memory content leaked into reply");
        }
        assertions.put("chatPrivacyHeld", !leaked && !privacyReply.isBlank());

        // 8. 匹配推荐接口可达（数量不做强断言，周任务时机不受控）
        if (assertions.get("loggedIn")) {
            getStep(recorder, steps, "match-recommendations",
                    baseUrl + "/api/match/recommendations", accessToken,
                    json -> json.path("code").asInt() == 200);
        } else {
            skipStep(steps, "match-recommendations", "logged-in did not pass");
        }
        assertions.put("matchApiReachable", passed(steps, "match-recommendations"));

        // 9. super-admin 注销收尾（也是本层数据的唯一清理途径）
        String adminUserName = System.getenv("YUSI_BENCHMARK_ADMIN_USER_NAME");
        String adminPassword = System.getenv("YUSI_BENCHMARK_ADMIN_PASSWORD");
        boolean adminConfigured = adminUserName != null && !adminUserName.isBlank()
                && adminPassword != null && !adminPassword.isBlank();
        if (!adminConfigured) {
            recorder.record("e2e:deregister", "CONFIG_MISSING",
                    "YUSI_BENCHMARK_ADMIN_USER_NAME/_PASSWORD not set; journey cleanup cannot run");
            failStep(steps, recorder, "admin-deregister", "CONFIG_MISSING",
                    "super admin credentials required");
        } else if (benchUserId.isBlank()) {
            failStep(steps, recorder, "admin-deregister", "USER_ID_UNKNOWN",
                    "deregistration target userId missing");
        } else {
            JsonNode adminLogin = postReturningBody(recorder, steps, "admin-login",
                    baseUrl + "/api/user/login", null,
                    Map.of("userName", adminUserName, "password", adminPassword),
                    json -> json.path("code").asInt() == 200);
            String adminToken = adminLogin.path("data").path("accessToken").asText("");
            if (adminToken.isBlank()) {
                failStep(steps, recorder, "admin-deregister", "ADMIN_LOGIN_FAILED",
                        "no token from admin login");
            } else {
                postStep(recorder, steps, "admin-deregister",
                        baseUrl + "/api/admin/users/" + benchUserId + "/deregister", adminToken,
                        Map.of(), json -> json.path("code").asInt() == 200);
            }
        }
        assertions.put("deregistered", passed(steps, "admin-deregister"));

        // 10. 注销后旧 token 必须被拒（业务码非 200 或非 2xx 都视为拒绝）
        if (assertions.get("deregistered") && !accessToken.isBlank()) {
            boolean denied;
            try {
                JsonNode result = raw("GET", baseUrl + "/api/diary/list", accessToken, null).json();
                denied = result.path("code").asInt(-1) != 200;
            } catch (Exception e) {
                denied = true;
            }
            if (!denied) {
                recorder.record("e2e:post-deletion-check", BenchmarkFailureRecorder.TYPE_E2E_STEP_ERROR,
                        "old token still accepted after deregister");
            }
            appendPassFail(steps, "post-deletion-check", denied, denied ? "" : "TOKEN_STILL_ACCEPTED");
            assertions.put("postDeletionDenied", denied);
        } else {
            appendPassFail(steps, "post-deletion-check", false, "skipped: deregistered=" + assertions.get("deregistered"));
            assertions.put("postDeletionDenied", false);
        }

        double layerAggregate = IrMetrics.round((double) assertions.values().stream()
                .filter(Boolean::booleanValue).count() / Math.max(1, assertions.size()));

        writePartFile(assertions, steps, layerAggregate, recorder);

        // gate 模式：显式开启时核心断言必须全绿才算通过
        if (BenchmarkEnv.gateEnabled()) {
            org.junit.jupiter.api.Assertions.assertTrue(layerAggregate >= 1.0d,
                    () -> "benchmark-e2e gate: aggregate=" + layerAggregate);
        }
    }

    // ---------- 配置 ----------

    private static String normalizedBaseUrl() {
        String configured = System.getenv("YUSI_BENCHMARK_BASE_URL");
        if (configured == null || configured.isBlank()) {
            return "http://127.0.0.1:8080";
        }
        return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
    }

    private static String shortUserName() {
        String candidate = "bench-e2e-" + BenchmarkEnv.runId();
        return candidate.length() <= 20 ? candidate : candidate.substring(0, 20);
    }

    // ---------- 注册验证码直写（唯一非 HTTP 步骤） ----------

    /**
     * 以最小 RESP 实现 SET auth:verification_code:{email} {code} PX 5min，等价于邮件送达。
     * 值必须写成 JSON 字符串（带引号）：产品侧 Redisson 默认 JsonJacksonCodec，
     * 裸文本会让服务端解码抛 JsonParseException 导致注册 500。
     */
    private static void writeRegisterCodeViaRedis(String email, String code) throws Exception {
        String host = envOrDefault("YUSI_BENCHMARK_REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(envOrDefault("YUSI_BENCHMARK_REDIS_PORT", "6379"));
        String password = System.getenv("YUSI_BENCHMARK_REDIS_PASSWORD");

        List<byte[]> commands = new ArrayList<>();
        if (password != null && !password.isBlank()) {
            commands.add(respEncode("AUTH", password));
        }
        byte[] setCommand = respEncode("SET",
                REGISTER_CODE_KEY_PREFIX + email, jsonString(code), "PX", String.valueOf(5 * 60_000L));
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5_000);
            socket.setSoTimeout(5_000);
            BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
            if (password != null && !password.isBlank()) {
                out.write(commands.get(0));
            }
            out.write(setCommand);
            out.flush();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            int expectedReplies = password != null && !password.isBlank() ? 2 : 1;
            List<String> replies = new ArrayList<>();
            String line;
            while (replies.size() < expectedReplies && (line = reader.readLine()) != null) {
                replies.add(line);
            }
            boolean setOk = replies.stream().anyMatch(reply -> reply.startsWith("+OK")
                    || reply.startsWith("$"));
            boolean authFailed = replies.stream().anyMatch(reply -> reply.startsWith("-ERR")
                    || reply.startsWith("-WRONGPASS") || reply.startsWith("-NOAUTH"));
            if (authFailed || !setOk) {
                throw new IllegalStateException("redis replied: " + String.join(",", replies));
            }
        }
    }

    /** 编码一条 RESP 多批量命令为数组字节。 */
    private static byte[] respEncode(String... args) {
        StringBuilder builder = new StringBuilder();
        builder.append('*').append(args.length).append("\r\n");
        for (String arg : args) {
            builder.append('$').append(arg.getBytes(StandardCharsets.UTF_8).length).append("\r\n")
                    .append(arg).append("\r\n");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 按 JsonJacksonCodec 的 JSON 字符串编码（转义反斜杠与引号）。 */
    private static String jsonString(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ---------- 单步执行助手 ----------

    private interface BodyCheck {
        boolean valid(JsonNode response);
    }

    private RawResp raw(String method, String url, String bearer, Object bodyObj) throws Exception {
        String jsonBody = bodyObj == null ? null : MAPPER.writeValueAsString(bodyObj);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(BenchmarkEnv.stepTimeoutSeconds("e2e-step")))
                .header("Content-Type", "application/json");
        if (bearer != null && !bearer.isBlank()) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        if (jsonBody == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        }
        HttpResponse<String> response =
                httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new RawResp(response.statusCode(), response.body());
    }

    /**
     * 统一单步执行：超时/传输异常记录对应失败类型；返回体 json 校验由 checker 决定；
     * 返回 null 表示该步失败（结果已写入 steps 与 anomalies）。
     */
    private JsonNode doStep(BenchmarkFailureRecorder recorder, List<StepOutcome> steps, String stepName,
            String method, String url, String bearer, Object bodyObj, BodyCheck checker) {
        long started = System.currentTimeMillis();
        try {
            RawResp response = recorder.withinTimeout(
                    BenchmarkEnv.stepTimeoutSeconds("e2e-step"),
                    () -> {
                        try {
                            return raw(method, url, bearer, bodyObj);
                        } catch (Exception e) {
                            throw new IllegalStateException(e.getMessage(), e);
                        }
                    });
            JsonNode json = response.json();
            boolean passed = response.status() / 100 == 2 && checker.valid(json);
            steps.add(new StepOutcome(stepName, passed, passed ? null : "ASSERTION_FAILED",
                    passed ? "" : "http=" + response.status(),
                    System.currentTimeMillis() - started,
                    passed ? "" : truncate(response.body())));
            if (!passed) {
                recorder.record("e2e:" + stepName, "ASSERTION_FAILED",
                        "http=" + response.status() + " | " + truncate(response.body()));
            }
            return passed ? json : null;
        } catch (java.util.concurrent.TimeoutException e) {
            recorder.record("e2e:" + stepName, BenchmarkFailureRecorder.TYPE_TIMEOUT, "step timeout");
            steps.add(new StepOutcome(stepName, false, BenchmarkFailureRecorder.TYPE_TIMEOUT,
                    "timeout", System.currentTimeMillis() - started, ""));
            return null;
        } catch (Exception e) {
            String message = BenchmarkFailureRecorder.LowRiskMessages.describe(e);
            recorder.record("e2e:" + stepName, BenchmarkFailureRecorder.TYPE_E2E_STEP_ERROR, message);
            steps.add(new StepOutcome(stepName, false, BenchmarkFailureRecorder.TYPE_E2E_STEP_ERROR,
                    message, System.currentTimeMillis() - started, ""));
            return null;
        }
    }

    private void postStep(BenchmarkFailureRecorder recorder, List<StepOutcome> steps, String stepName,
            String url, String bearer, Object bodyObj, BodyCheck checker) {
        doStep(recorder, steps, stepName, "POST", url, bearer, bodyObj, checker);
    }

    private void patchStep(BenchmarkFailureRecorder recorder, List<StepOutcome> steps, String stepName,
            String url, String bearer, Object bodyObj) {
        doStep(recorder, steps, stepName, "PATCH", url, bearer, bodyObj,
                json -> json.path("code").asInt() == 200);
    }

    private void getStep(BenchmarkFailureRecorder recorder, List<StepOutcome> steps, String stepName,
            String url, String bearer, BodyCheck checker) {
        doStep(recorder, steps, stepName, "GET", url, bearer, null, checker);
    }

    /** 返回完整返回体的 POST（用于需要读取 data 的步骤）。 */
    private JsonNode postReturningBody(BenchmarkFailureRecorder recorder, List<StepOutcome> steps,
            String stepName, String url, String bearer, Object bodyObj, BodyCheck checker) {
        JsonNode result = doStep(recorder, steps, stepName, "POST", url, bearer, bodyObj, checker);
        return result == null ? MAPPER.createObjectNode() : result;
    }

    private void failStep(List<StepOutcome> steps, BenchmarkFailureRecorder recorder, String stepName,
            String failureType, String detail) {
        steps.add(new StepOutcome(stepName, false, failureType, detail, 0, ""));
        recorder.record("e2e:" + stepName, failureType, detail);
    }

    private void skipStep(List<StepOutcome> steps, String stepName, String reason) {
        steps.add(new StepOutcome(stepName, false, "SKIPPED_DEPENDENCY_FAILED", reason, 0, ""));
    }

    private void appendPassFail(List<StepOutcome> steps, String stepName, boolean passed, String detail) {
        steps.add(new StepOutcome(stepName, passed, passed ? null : "ASSERTION_FAILED", detail, 0, ""));
    }

    private static boolean passed(List<StepOutcome> steps, String stepName) {
        return steps.stream()
                .filter(outcome -> outcome.step().equals(stepName))
                .reduce((first, second) -> second)
                .map(StepOutcome::passed)
                .orElse(false);
    }

    // ---------- 对话流式 ----------

    /** 建立 SSE 连接收集 response.delta 文本，直到 run.completed / run.failed / 超时。 */
    private String streamChat(BenchmarkFailureRecorder recorder, List<StepOutcome> steps, String baseUrl,
            String accessToken, String stepName, String message) {
        long deadline = System.currentTimeMillis()
                + BenchmarkEnv.stepTimeoutSeconds("e2e-step") * 1_000L;
        StringBuilder reply = new StringBuilder();
        long started = System.currentTimeMillis();
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/ai/chat/stream"))
                    .timeout(Duration.ofSeconds(BenchmarkEnv.stepTimeoutSeconds("e2e-step")))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(Map.of(
                            "requestId", BenchmarkEnv.runId() + "-" + stepName,
                            "message", message)), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<java.io.InputStream> response;
            while (true) {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 429) {
                    break;
                }
                // 服务端单用户 AI 锁（前一个流式请求仍在处理）：等待后重试，直到步骤超时
                if (System.currentTimeMillis() >= deadline) {
                    break;
                }
                Thread.sleep(15_000L);
            }
            if (response.statusCode() / 100 != 2) {
                String excerpt = truncate(new String(response.body().readAllBytes(),
                        StandardCharsets.UTF_8));
                recorder.record("e2e:" + stepName, "ASSERTION_FAILED", "http=" + response.statusCode());
                steps.add(new StepOutcome(stepName, false, "ASSERTION_FAILED",
                        "http=" + response.statusCode(), System.currentTimeMillis() - started, excerpt));
                return "";
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                boolean completed = false;
                while (!completed && System.currentTimeMillis() < deadline
                        && (line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring("data:".length()).trim();
                    if (payload.isEmpty() || "[DONE]".equals(payload)) {
                        continue;
                    }
                    JsonNode event;
                    try {
                        event = MAPPER.readTree(payload);
                    } catch (Exception parseError) {
                        continue;
                    }
                    switch (event.path("type").asText("")) {
                        case "response.delta" -> reply.append(event.path("text").asText(""));
                        case "run.completed" -> completed = true;
                        case "run.failed" -> {
                            recorder.record("e2e:" + stepName, BenchmarkFailureRecorder.TYPE_MODEL_ERROR,
                                    "agent run failed event received");
                            steps.add(new StepOutcome(stepName, false,
                                    BenchmarkFailureRecorder.TYPE_MODEL_ERROR, "run.failed event",
                                    System.currentTimeMillis() - started, truncate(reply.toString())));
                            return reply.toString();
                        }
                        default -> { /* stage/tool 事件只用于进度展示 */ }
                    }
                }
                if (!completed && System.currentTimeMillis() >= deadline) {
                    recorder.record("e2e:" + stepName, BenchmarkFailureRecorder.TYPE_TIMEOUT,
                            "stream exceeded timeout before run.completed");
                    steps.add(new StepOutcome(stepName, false, BenchmarkFailureRecorder.TYPE_TIMEOUT,
                            "partial reply collected", System.currentTimeMillis() - started,
                            truncate(reply.toString())));
                    return reply.toString();
                }
                steps.add(new StepOutcome(stepName, !reply.isEmpty(),
                        reply.isEmpty() ? "EMPTY_REPLY" : null,
                        "", System.currentTimeMillis() - started, ""));
                return reply.toString();
            }
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            recorder.record("e2e:" + stepName, BenchmarkFailureRecorder.TYPE_E2E_STEP_ERROR,
                    BenchmarkFailureRecorder.LowRiskMessages.describe(e));
            steps.add(new StepOutcome(stepName, false, BenchmarkFailureRecorder.TYPE_E2E_STEP_ERROR,
                    BenchmarkFailureRecorder.LowRiskMessages.describe(e),
                    System.currentTimeMillis() - started, ""));
            return "";
        }
    }

    // ---------- 工具方法 ----------

    /** 返回 ObjectNode 而非 String：raw() 会对 body 统一序列化，返回 String 会造成双重 JSON 编码。 */
    private com.fasterxml.jackson.databind.node.ObjectNode diaryPayload(String title, String content) {
        return MAPPER.createObjectNode()
                .put("title", title)
                .put("content", content)
                .put("plainContent", content)
                .put("clientEncrypted", false)
                .put("visibility", true)
                .put("entryDate", LocalDate.now().toString());
    }

    private static String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String sanitized = text.replaceAll("[\\r\\n]+", " ");
        return sanitized.length() <= 300 ? sanitized : sanitized.substring(0, 300);
    }

    private void writePartFile(Map<String, Boolean> assertions, List<StepOutcome> steps,
            double layerAggregate, BenchmarkFailureRecorder recorder) throws Exception {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("stepCount", steps.size());
        metrics.put("failedStepCount", steps.stream().filter(step -> !step.passed()).count());
        metrics.put("assertionCount", assertions.size());
        metrics.put("assertionPassedCount", assertions.values().stream().filter(Boolean::booleanValue).count());
        metrics.putAll(assertions);

        Map<String, Object> part = new LinkedHashMap<>();
        part.put("layer", "e2e");
        part.put("layerLabel", "e2e-journey");
        part.put("env", BenchmarkEnv.env());
        part.put("runId", BenchmarkEnv.runId());
        part.put("fixtureVersion", BenchmarkEnv.FIXTURES_VERSION);
        part.put("generatedAt", Instant.now().toString());
        part.put("aggregateScores", Map.of("e2e", layerAggregate));
        part.put("metrics", metrics);
        part.put("perStep", steps);
        part.put("anomalies", recorder.failures());

        Path partPath = Path.of("target", "benchmark", "parts", "e2e.json");
        Files.createDirectories(partPath.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(partPath.toFile(), part);
    }
}
