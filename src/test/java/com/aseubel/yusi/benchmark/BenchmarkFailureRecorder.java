package com.aseubel.yusi.benchmark;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * benchmark 运行期的失败收集器。
 * 任何 timeout / 模型失败 / judge 解析失败 / retrieval 失败 / cleanup 失败都必须经过这里记录，
 * 最终写入记分卡 anomalies，禁止静默吞掉（record-only 模式：记录后继续跑完其余 case）。
 */
public final class BenchmarkFailureRecorder {

    public static final String TYPE_TIMEOUT = "TIMEOUT";
    public static final String TYPE_MODEL_ERROR = "MODEL_ERROR";
    public static final String TYPE_JUDGE_PARSE_ERROR = "JUDGE_PARSE_ERROR";
    public static final String TYPE_RETRIEVAL_ERROR = "RETRIEVAL_ERROR";
    public static final String TYPE_EXTRACTION_ERROR = "EXTRACTION_ERROR";
    public static final String TYPE_E2E_STEP_ERROR = "E2E_STEP_ERROR";
    public static final String TYPE_CLEANUP_ERROR = "CLEANUP_ERROR";
    public static final String TYPE_FIXTURE_ERROR = "FIXTURE_ERROR";

    /** 单条失败事件：step 定位到层/用例，failureType 为枚举字符串，message 只保留摘要不含敏感正文。 */
    public record Failure(String step, String failureType, String message, Instant occurredAt) {
    }

    private final List<Failure> failures = new CopyOnWriteArrayList<>();

    public void record(String step, String failureType, String message) {
        failures.add(new Failure(step, failureType == null ? "UNKNOWN" : failureType,
                message == null ? "" : message, Instant.now()));
    }

    /** 按 step 包裹执行：异常被记录并计 fallback 值，不向上抛出（超时请配合 withinTimeout 使用）。 */
    public <T> T guard(String step, String failureType, java.util.function.Supplier<T> action,
            java.util.function.Supplier<T> fallback) {
        try {
            return action.get();
        } catch (Exception e) {
            record(step, failureType, LowRiskMessages.describe(e));
            return fallback.get();
        }
    }

    /**
     * 在时间上限内阻塞执行；超时抛出 TimeoutException（取消任务），
     * 执行异常以 RuntimeException 重新抛出——记录责任统一由调用方承担，避免双记。
     */
    public <T> T withinTimeout(int timeoutSeconds, java.util.function.Supplier<T> action)
            throws java.util.concurrent.TimeoutException {
        java.util.concurrent.CompletableFuture<T> future =
                java.util.concurrent.CompletableFuture.supplyAsync(action);
        try {
            return future.get(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw cause instanceof RuntimeException runtime ? runtime : new IllegalStateException(cause);
        }
    }

    public List<Failure> failures() {
        return List.copyOf(failures);
    }

    public boolean isEmpty() {
        return failures.isEmpty();
    }

    /** 低敏摘要：只取异常类名与首条消息片段，避免把模型输出原文写进记分卡。 */
    static final class LowRiskMessages {
        private LowRiskMessages() {
        }

        static String describe(Throwable t) {
            // 递归到 root cause：包装异常（如 IllegalStateException(null, cause)）不丢真实原因
            Throwable root = t;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            String className = root.getClass().getSimpleName();
            String message = root.getMessage();
            if (message == null || message.isBlank()) {
                return className;
            }
            String sanitized = message.replaceAll("[\\r\\n]+", " ");
            return className + ": "
                    + sanitized.substring(0, Math.min(sanitized.length(), 200));
        }
    }
}
