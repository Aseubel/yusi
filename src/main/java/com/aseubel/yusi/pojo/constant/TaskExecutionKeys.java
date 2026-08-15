package com.aseubel.yusi.pojo.constant;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Stable idempotency key builders for task execution records. */
public final class TaskExecutionKeys {

    private static final int MAX_KEY_LENGTH = 191;

    private TaskExecutionKeys() {
    }

    public static String fromEvent(TaskExecutionType taskType, String sourceType,
            String sourceId, String eventId) {
        return compact(taskType.code().toLowerCase() + ":event:" + sourceType + ":"
                + sourceId + ":" + eventId);
    }

    public static String fromSourceRevision(TaskExecutionType taskType, String ownerUserId,
            String sourceType, String sourceId, Long sourceRevision) {
        long revision = SourceRevision.initialOrCurrent(sourceRevision);
        return compact(taskType.code().toLowerCase() + ":revision:" + revision + ":"
                + ownerUserId + ":" + sourceType + ":" + sourceId);
    }

    public static String scheduled(TaskExecutionType taskType, String sourceId, String runId) {
        return compact(taskType.code().toLowerCase() + ":scheduled:" + sourceId + ":" + runId);
    }

    public static String daily(TaskExecutionType taskType, String ownerUserId,
            String sourceId, LocalDate bucket) {
        return compact(taskType.code().toLowerCase() + ":daily:" + ownerUserId + ":"
                + sourceId + ":" + (bucket == null ? "unknown" : bucket));
    }

    public static String invocation(TaskExecutionType taskType, String ownerUserId,
            String sourceId, String invocationId) {
        return compact(taskType.code().toLowerCase() + ":invocation:" + ownerUserId + ":"
                + sourceId + ":" + invocationId);
    }

    private static String compact(String raw) {
        if (raw.length() <= MAX_KEY_LENGTH) {
            return raw;
        }
        return raw.substring(0, 32) + ":" + sha256(raw);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
