package com.aseubel.yusi.pojo.dto.chat;

/**
 * A user-facing event emitted while one chat AgentRun is executing.
 *
 * <p>Tool arguments, raw model thinking, and tool results are intentionally
 * excluded. The client only needs safe lifecycle metadata to render progress.
 */
public record AgentStreamEvent(
        String type,
        String runId,
        String stage,
        String status,
        String toolCallId,
        String toolName,
        String toolSource,
        Boolean success,
        Long durationMs,
        String text,
        String message) {

    public static AgentStreamEvent runStarted(String runId) {
        return new AgentStreamEvent("run.started", runId, "preparing", "running", null, null, null, null, null,
                null, null);
    }

    public static AgentStreamEvent stage(String runId, String stage) {
        return new AgentStreamEvent("run.stage", runId, stage, "running", null, null, null, null, null, null,
                null);
    }

    public static AgentStreamEvent toolStarted(String runId, String toolCallId, String toolName, String toolSource) {
        return new AgentStreamEvent("tool.started", runId, "tool", "running", toolCallId, toolName, toolSource,
                null, null, null, null);
    }

    public static AgentStreamEvent toolCompleted(String runId, String toolCallId, String toolName, String toolSource,
            boolean success, Long durationMs) {
        return new AgentStreamEvent("tool.completed", runId, "tool", success ? "completed" : "failed", toolCallId,
                toolName, toolSource, success, durationMs, null, null);
    }

    public static AgentStreamEvent responseDelta(String runId, String text) {
        return new AgentStreamEvent("response.delta", runId, "responding", "streaming", null, null, null, null,
                null, text, null);
    }

    public static AgentStreamEvent runCompleted(String runId) {
        return new AgentStreamEvent("run.completed", runId, "completed", "completed", null, null, null, true,
                null, null, null);
    }

    public static AgentStreamEvent runFailed(String runId) {
        return new AgentStreamEvent("run.failed", runId, "failed", "failed", null, null, null, false, null, null,
                "AI response failed");
    }
}
