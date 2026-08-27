package com.aseubel.yusi.common.task.scheduler;

import com.aseubel.yusi.common.task.DistributedJobRunner;
import com.aseubel.yusi.common.task.MemoryScheduledTasks;
import com.aseubel.yusi.monitor.InterfaceUsageMonitor;
import com.aseubel.yusi.observability.metrics.YusiMetrics;
import com.aseubel.yusi.observability.task.TaskHealthRegistry;
import com.aseubel.yusi.observability.trace.TraceIdSupport;
import com.aseubel.yusi.service.agent.AgentProactiveService;
import com.aseubel.yusi.service.ai.embedding.EmbeddingBatchService;
import com.aseubel.yusi.service.ai.model.ModelStateCenter;
import com.aseubel.yusi.service.cognition.MidMemoryFusionService;
import com.aseubel.yusi.service.lifegraph.LifeGraphMergeSuggestionService;
import com.aseubel.yusi.service.lifegraph.LifeGraphTaskBatchService;
import com.aseubel.yusi.service.match.MatchService;
import com.aseubel.yusi.service.report.SoulReportGenerator;
import com.aseubel.yusi.service.room.RoomScheduler;
import com.aseubel.yusi.service.security.SecurityAuditService;
import com.aseubel.yusi.service.task.TaskExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.time.LocalDateTime;

/**
 * Central registry for application-level scheduled entry points.
 * Business services expose ordinary methods; scheduling and cluster
 * coordination stay in this infrastructure boundary.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class YusiScheduledTasks {

    private final DistributedJobRunner jobRunner;
    private final InterfaceUsageMonitor interfaceUsageMonitor;
    private final MemoryScheduledTasks memoryScheduledTasks;
    private final RoomScheduler roomScheduler;
    private final MidMemoryFusionService midMemoryFusionService;
    private final AgentProactiveService agentProactiveService;
    private final EmbeddingBatchService embeddingBatchService;
    private final ModelStateCenter modelStateCenter;
    private final LifeGraphTaskBatchService lifeGraphTaskBatchService;
    private final LifeGraphMergeSuggestionService lifeGraphMergeSuggestionService;
    private final SoulReportGenerator soulReportGenerator;
    private final MatchService matchService;
    private final TaskExecutionService taskExecutionService;
    private final SecurityAuditService securityAuditService;
    private final TaskHealthRegistry taskHealthRegistry;
    private final YusiMetrics metrics;
    private static final long MODEL_STATE_SYNC_INTERVAL_MS = 30_000L;

    @Scheduled(cron = "0 0/30 * * * ?")
    public void syncInterfaceUsage() {
        jobRunner.runIfLeader("usage-sync", interfaceUsageMonitor::syncToDatabase);
    }

    @Scheduled(cron = "#{@memoryConfigProperties.midTermScanCron}")
    public void scanAndSummarizeMidTermMemory() {
        jobRunner.runIfLeader("memory-scan", memoryScheduledTasks::scanAndSummarizeMidTermMemory);
    }

    @Scheduled(fixedRate = 60000)
    public void dissolveExpiredRooms() {
        jobRunner.runIfLeader("room-cleanup", roomScheduler::dissolveExpiredRooms);
    }

    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Shanghai")
    public void fuseMidMemory() {
        jobRunner.runIfLeader("memory-fusion", midMemoryFusionService::runFusion);
    }

    @Scheduled(cron = "0 0 */1 * * ?")
    public void scanAndGreet() {
        jobRunner.runIfLeader("proactive-greeting", agentProactiveService::scanAndGreet);
    }

    @Scheduled(fixedDelay = 1000)
    public void processEmbeddingTasks() {
        runTracked("embedding-worker", embeddingBatchService::processPendingTasks);
    }

    @Scheduled(fixedDelay = 60000)
    public void recoverEmbeddingTasks() {
        runTracked("embedding-worker", embeddingBatchService::recoverStaleTasks);
    }

    @Scheduled(fixedDelay = 3600000)
    public void cleanupEmbeddingTasks() {
        jobRunner.runIfLeader("embedding-cleanup", embeddingBatchService::cleanupCompletedTasks);
    }

    @Scheduled(fixedDelay = MODEL_STATE_SYNC_INTERVAL_MS)
    public void syncModelState() {
        runTracked("model-state-sync", modelStateCenter::syncToRedis);
    }

    @Scheduled(fixedDelay = 2000)
    public void processLifeGraphTasks() {
        runTracked("lifegraph-worker", lifeGraphTaskBatchService::processPendingTasks);
    }

    @Scheduled(fixedDelay = 60000)
    public void recoverLifeGraphTasks() {
        runTracked("lifegraph-worker", lifeGraphTaskBatchService::recoverStaleTasks);
    }

    @Scheduled(fixedDelay = 3600000)
    public void cleanupLifeGraphTasks() {
        jobRunner.runIfLeader("lifegraph-cleanup", lifeGraphTaskBatchService::cleanupCompletedTasks);
    }

    @Scheduled(fixedDelay = 60000)
    public void recoverTaskExecutions() {
        jobRunner.runIfLeader("task-execution-recovery",
                () -> taskExecutionService.recoverStaleTasks(LocalDateTime.now()));
    }

    @Scheduled(cron = "0 30 3 * * ?", zone = "Asia/Shanghai")
    public void cleanupSecurityAuditEvents() {
        jobRunner.runIfLeader("security-audit-cleanup",
                () -> securityAuditService.cleanupExpired(LocalDateTime.now()));
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void generateLifeGraphMergeSuggestions() {
        jobRunner.runIfLeader("lifegraph-merge-suggestion",
                lifeGraphMergeSuggestionService::scheduledMergeSuggestion);
    }

    @Scheduled(cron = "0 0 22 ? * SUN", zone = "Asia/Shanghai")
    public void generateWeeklyReports() {
        jobRunner.runIfLeader("weekly-report", soulReportGenerator::generateWeeklyReports);
    }

    @Scheduled(cron = "0 0 20 ? * FRI", zone = "Asia/Shanghai")
    public void runWeeklyMatching() {
        jobRunner.runIfLeader("weekly-match", matchService::runWeeklyMatching);
    }

    private void runTracked(String taskName, Runnable task) {
        taskHealthRegistry.recordStart(taskName);
        try {
            TraceIdSupport.withTraceId("job_" + taskName, task);
            taskHealthRegistry.recordSuccess(taskName);
            metrics.recordTask(taskName, "success");
        } catch (RuntimeException exception) {
            taskHealthRegistry.recordFailure(taskName, classify(exception));
            metrics.recordTask(taskName, "failure");
            log.error("Scheduled task failed: task={}, exceptionType={}", taskName,
                    exception.getClass().getSimpleName());
        }
    }

    private String classify(RuntimeException exception) {
        String type = exception.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        if (type.contains("timeout")) {
            return "timeout";
        }
        if (type.contains("connect") || type.contains("redis")) {
            return "connection_failure";
        }
        return "unknown";
    }
}
