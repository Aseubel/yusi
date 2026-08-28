package com.aseubel.yusi.benchmark;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Benchmark 运行环境与本次运行的唯一标识。
 * 全部通过系统属性 / 环境变量注入，默认 local。
 */
public final class BenchmarkEnv {

    /** 所有合成数据用户 ID 前缀；收尾按此前缀清理。 */
    public static final String USER_PREFIX = "bench-";
    /** benchmark 专用 Milvus 集合前缀（与 application-benchmark.yml 一致）。 */
    public static final String COLLECTION_PREFIX = "yusi_benchmark_";
    /** fixture 数据契约版本：变更语料或 gold 标注时必须递增。 */
    public static final String FIXTURES_VERSION = "v1";

    private static final String RUN_ID =
            UUID.randomUUID().toString().substring(0, 8);
    private static final Instant STARTED_AT = Instant.now();

    private BenchmarkEnv() {
    }

    public static String runId() {
        return RUN_ID;
    }

    public static Instant startedAt() {
        return STARTED_AT;
    }

    /** 本次运行总时长上限，供 runner 判断是否来得及继续。 */
    public static Duration elapsed() {
        return Duration.between(STARTED_AT, Instant.now());
    }

    /** 运行环境标签：local / server。 */
    public static String env() {
        String property = System.getProperty("yusi.benchmark.env");
        if (property != null && !property.isBlank()) {
            return property;
        }
        String fromEnv = System.getenv("YUSI_BENCHMARK_ENV");
        return fromEnv == null || fromEnv.isBlank() ? "local" : fromEnv;
    }

    /** 硬门槛模式：默认 record-only。 */
    public static boolean gateEnabled() {
        return Boolean.parseBoolean(System.getProperty("yusi.benchmark.gate", "false"));
    }

    /** 每层单步超时秒数，超时计入记分卡 anomaly 而不是静默吞掉。 */
    public static int stepTimeoutSeconds(String step) {
        return Integer.getInteger("yusi.benchmark.timeout." + step, defaultStepTimeout(step));
    }

    private static int defaultStepTimeout(String step) {
        return switch (step) {
            case "judge" -> 120;
            case "retrieval-query" -> 30;
            case "extraction-task" -> 600;
            case "match-rerank" -> 90;
            case "match-letter" -> 180;
            case "e2e-step" -> 180;
            default -> 60;
        };
    }

    /** 生成本次运行唯一 bench 用户 ID：bench-{tag}-{runId}。 */
    public static String userId(String tag) {
        return USER_PREFIX + tag + "-" + RUN_ID;
    }

    /** 当前所有 layer runner 是否沿用同一用户命名约定。 */
    public static boolean isBenchUser(String userId) {
        return userId != null && userId.startsWith(USER_PREFIX);
    }
}
