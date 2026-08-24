package com.aseubel.yusi.observability.alert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** Asynchronous Feishu transport with fixed payload and failure classifications. */
@Slf4j
public class FeishuAlertNotifier {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final FeishuAlertProperties properties;
    private final WebhookClient client;
    private final Executor executor;
    private final Clock clock;

    public FeishuAlertNotifier(FeishuAlertProperties properties, WebhookClient client,
            Executor executor, Clock clock) {
        this.properties = properties == null
                ? new FeishuAlertProperties(false, "", "") : properties;
        this.client = client;
        this.executor = executor == null ? Runnable::run : executor;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public void notify(AlertMessage message) {
        if (message == null || !properties.configured() || client == null) {
            return;
        }
        try {
            executor.execute(() -> deliver(message));
        } catch (RejectedExecutionException exception) {
            log.warn("Feishu alert delivery skipped: category=queue_full, attempt=0, exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }

    public static String renderPayload(AlertMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("alert message is required");
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("msg_type", "interactive");
        envelope.put("card", renderCard(message));
        try {
            return JSON.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("alert payload construction failed", exception);
        }
    }

    private void deliver(AlertMessage message) {
        String payload = renderPayload(message);
        Instant timestamp = clock.instant();
        for (int attempt = 1; attempt <= properties.maxDeliveryAttempts(); attempt++) {
            try {
                client.send(new WebhookRequest(properties.webhookUrl(), payload,
                        sign(timestamp, properties.signingSecret()), true));
                return;
            } catch (RuntimeException exception) {
                String category = classify(exception);
                log.warn("Feishu alert delivery failed: category={}, attempt={}, backoffClass={}, exceptionType={}",
                        category, attempt, backoffClass(attempt), exception.getClass().getSimpleName());
                if (attempt < properties.maxDeliveryAttempts() && !sleepBackoff(attempt)) {
                    return;
                }
            }
        }
    }

    private boolean sleepBackoff(int attempt) {
        long delayMillis = 25L << Math.min(2, Math.max(0, attempt - 1));
        try {
            Thread.sleep(delayMillis);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Feishu alert delivery stopped: category=interrupted, attempt={}, backoffClass={}, exceptionType={}",
                    attempt, backoffClass(attempt), exception.getClass().getSimpleName());
            return false;
        }
    }

    private String sign(Instant timestamp, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp.getEpochSecond() + "\n" + secret)
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception exception) {
            return "invalid";
        }
    }

    private String classify(RuntimeException exception) {
        String type = exception.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        if (type.contains("timeout")) {
            return "timeout";
        }
        if (type.contains("connect") || type.contains("socket")) {
            return "connection_failure";
        }
        return "http_failure";
    }

    private String backoffClass(int attempt) {
        return attempt <= 1 ? "short" : attempt == 2 ? "medium" : "long";
    }

    private static Map<String, Object> renderCard(AlertMessage message) {
        CardDescriptor descriptor = CardDescriptor.from(message);
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("config", Map.of("wide_screen_mode", true, "enable_forward", true));
        card.put("header", Map.of(
                "template", descriptor.template(),
                "title", Map.of("tag", "plain_text", "content", descriptor.header())));

        List<Map<String, Object>> elements = new java.util.ArrayList<>();
        elements.add(fields(
                field("告警编号", descriptor.code()),
                field("状态", descriptor.stateLabel()),
                field("级别", descriptor.levelLabel()),
                field("分类", descriptor.classificationLabel())));
        elements.add(fields(
                field("服务", message.service()),
                field("操作", descriptor.operationLabel()),
                field("窗口", message.window()),
                field("观测时间", message.observedAt().toString())));
        elements.add(fields(
                field("统计次数", descriptor.countLabel() + "：" + message.count()),
                field("当前值", descriptor.valueLabel() + "：" + descriptor.value())));
        elements.add(textBlock("影响", descriptor.impact()));
        elements.add(textBlock("触发规则", descriptor.condition()));
        elements.add(textBlock("建议处理", descriptor.action()));
        elements.add(Map.of(
                "tag", "note",
                "elements", List.of(Map.of(
                        "tag", "plain_text",
                        "content", descriptor.machineSummary(message)))));
        card.put("elements", elements);
        return card;
    }

    private static Map<String, Object> fields(Map<String, Object>... values) {
        return Map.of("tag", "div", "fields", List.of(values));
    }

    private static Map<String, Object> field(String label, String value) {
        return Map.of(
                "is_short", true,
                "text", Map.of("tag", "lark_md", "content", "**" + label + "**\n" + value));
    }

    private static Map<String, Object> textBlock(String title, String value) {
        return Map.of(
                "tag", "div",
                "text", Map.of("tag", "lark_md", "content", "**" + title + "**\n" + value));
    }

    private record CardDescriptor(
            String code,
            String title,
            String template,
            String operationLabel,
            String levelLabel,
            String stateLabel,
            String classificationLabel,
            String countLabel,
            String valueLabel,
            String value,
            String impact,
            String condition,
            String action) {

        private static CardDescriptor from(AlertMessage message) {
            String operationLabel = operationLabel(message.operation());
            String classificationLabel = classificationLabel(message.classification());
            String levelLabel = "critical".equals(message.level()) ? "严重" : "警告";
            String stateLabel = "recovered".equals(message.state()) ? "已恢复" : "触发中";
            String template = "recovered".equals(message.state())
                    ? "green" : "critical".equals(message.level()) ? "red" : "orange";

            return switch (message.category()) {
                case "service_unavailable" -> service(message, operationLabel, levelLabel,
                        stateLabel, classificationLabel, template);
                case "model_failure_rate" -> new CardDescriptor(
                        "YUSI-MODEL-FAILURE-RATE", "模型调用失败率升高", template, operationLabel,
                        levelLabel, stateLabel, classificationLabel, "模型调用样本",
                        "失败率", percentage(message.value()),
                        "模型请求成功率下降，可能触发模型降级。",
                        "5m 失败率 >= 20%，且样本数 >= 20（初始值，待生产调优）。",
                        "检查模型网关健康状态、失败分类和现有降级路径，确认失败率是否持续。");
                case "task_backlog" -> new CardDescriptor(
                        "YUSI-TASK-BACKLOG", "后台任务积压 · " + operationLabel, template, operationLabel,
                        levelLabel, stateLabel, classificationLabel, "积压信号",
                        "延迟", message.value() + " min",
                        "后台任务处理延迟，数据新鲜度可能下降。",
                        taskCondition(message.level()),
                        "检查任务调度、执行状态、依赖健康和积压时长。");
                case "budget_denied" -> new CardDescriptor(
                        "YUSI-BUDGET-ADMISSION", "预算准入拒绝激增", template, operationLabel,
                        levelLabel, stateLabel, classificationLabel, "窗口拒绝",
                        "拒绝次数", message.count(),
                        "模型请求被预算准入拒绝，相关功能可能降级。",
                        "5m 拒绝次数 >= 10（初始值，待生产调优）。",
                        "检查预算准入存储、配额策略和拒绝分类，确认是否为持续性拒绝。");
                default -> new CardDescriptor(
                        "YUSI-ALERT-UNKNOWN", "告警信号", template, operationLabel, levelLabel,
                        stateLabel, classificationLabel, "统计次数", "当前值", message.value(),
                        "告警信号需要人工确认。", "当前信号已通过低敏归一化。",
                        "检查 readiness、dependency_health 和对应指标。");
            };
        }

        private static CardDescriptor service(AlertMessage message, String operationLabel,
                String levelLabel, String stateLabel, String classificationLabel, String template) {
            boolean readiness = "readiness".equals(message.operation());
            String dependency = switch (message.operation()) {
                case "db" -> "MySQL";
                case "redis" -> "Redis";
                case "milvus" -> "Milvus";
                case "model_gateway" -> "模型网关";
                default -> "服务";
            };
            String code = readiness ? "YUSI-SVC-READINESS" : "YUSI-DEP-" + message.operation().toUpperCase(Locale.ROOT);
            String title = readiness ? "服务不可用 · 就绪检查" : "依赖不可用 · " + dependency;
            String impact = readiness
                    ? "实例当前不应接收新流量。"
                    : dependency + "异常可能影响对应业务能力。";
            String condition = readiness
                    ? "连续 DOWN >= 2m（初始值，待生产调优）。"
                    : "dependency_health=0 且持续异常（初始值，待生产调优）。";
            String action = readiness
                    ? "检查 readiness 与 dependency_health，确认各关键依赖状态。"
                    : "检查 " + dependency + " 连通性、响应耗时和对应健康探针。";
            String value = "available".equals(message.classification()) ? "UP (1.0)" : "DOWN (" + message.value() + ")";
            return new CardDescriptor(code, title, template, operationLabel, levelLabel, stateLabel,
                    classificationLabel, readiness ? "检测信号" : "依赖信号", "健康状态", value,
                    impact, condition, action);
        }

        private String header() {
            String severity = "critical".equalsIgnoreCase(levelLabel) ? "严重" : "警告";
            return "[" + severity + "] " + title + " · " + stateLabel;
        }

        private String machineSummary(AlertMessage message) {
            return "alert_code=" + code
                    + " | alert_category=" + message.category()
                    + " | service=" + message.service()
                    + " | operation=" + message.operation()
                    + " | level=" + message.level()
                    + " | window=" + message.window()
                    + " | count=" + message.count()
                    + " | value=" + message.value()
                    + " | classification=" + message.classification()
                    + " | observed_at=" + message.observedAt()
                    + " | state=" + message.state();
        }

        private static String operationLabel(String operation) {
            return switch (operation) {
                case "readiness" -> "就绪检查";
                case "db" -> "MySQL";
                case "redis" -> "Redis";
                case "milvus" -> "Milvus";
                case "model_gateway" -> "模型网关";
                case "model_call" -> "模型调用";
                case "model_admission" -> "模型预算准入";
                default -> operation;
            };
        }

        private static String classificationLabel(String classification) {
            return switch (classification) {
                case "available" -> "可用";
                case "none" -> "无异常";
                case "timeout" -> "超时";
                case "connection_failure" -> "连接失败";
                case "unavailable" -> "不可用";
                case "validation" -> "校验失败";
                case "rejected" -> "被拒绝";
                case "dependency" -> "依赖异常";
                case "admission_store_unavailable" -> "准入存储不可用";
                case "reservation_conflict" -> "预留冲突";
                case "limit_exceeded" -> "额度超限";
                case "failure_rate" -> "失败率";
                case "budget_denied" -> "预算拒绝";
                default -> "未知";
            };
        }

        private static String percentage(String value) {
            try {
                return String.format(Locale.ROOT, "%.2f%%", Double.parseDouble(value) * 100D);
            } catch (RuntimeException ignored) {
                return "unknown";
            }
        }

        private static String taskCondition(String level) {
            return ("critical".equals(level) ? "延迟 >= 60m" : "延迟 >= 15m")
                    + "，持续 >= 5m（初始值，待生产调优）。";
        }
    }

    public interface WebhookClient {
        void send(WebhookRequest request);
    }

    public record WebhookRequest(String destination, String payload, String signature, boolean signaturePresent) {
        @Override
        public String toString() {
            return "WebhookRequest[payloadPresent=" + (payload != null)
                    + ", signaturePresent=" + signaturePresent + "]";
        }
    }
}
