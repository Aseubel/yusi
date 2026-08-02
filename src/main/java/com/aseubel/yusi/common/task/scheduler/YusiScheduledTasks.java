package com.aseubel.yusi.common.task.scheduler;

import com.aseubel.yusi.common.task.DistributedJobRunner;
import com.aseubel.yusi.common.task.MemoryScheduledTasks;
import com.aseubel.yusi.monitor.InterfaceUsageMonitor;
import com.aseubel.yusi.service.agent.AgentProactiveService;
import com.aseubel.yusi.service.ai.embedding.EmbeddingBatchService;
import com.aseubel.yusi.service.ai.model.ModelStateCenter;
import com.aseubel.yusi.service.cognition.MidMemoryFusionService;
import com.aseubel.yusi.service.lifegraph.LifeGraphMergeSuggestionService;
import com.aseubel.yusi.service.lifegraph.LifeGraphTaskBatchService;
import com.aseubel.yusi.service.match.MatchService;
import com.aseubel.yusi.service.report.SoulReportGenerator;
import com.aseubel.yusi.service.room.RoomScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Central registry for application-level scheduled entry points.
 * Business services expose ordinary methods; scheduling and cluster
 * coordination stay in this infrastructure boundary.
 */
@Component
@RequiredArgsConstructor
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
        embeddingBatchService.processPendingTasks();
    }

    @Scheduled(fixedDelay = 60000)
    public void recoverEmbeddingTasks() {
        embeddingBatchService.recoverStaleTasks();
    }

    @Scheduled(fixedDelay = 3600000)
    public void cleanupEmbeddingTasks() {
        jobRunner.runIfLeader("embedding-cleanup", embeddingBatchService::cleanupCompletedTasks);
    }

    @Scheduled(fixedDelay = MODEL_STATE_SYNC_INTERVAL_MS)
    public void syncModelState() {
        modelStateCenter.syncToRedis();
    }

    @Scheduled(fixedDelay = 2000)
    public void processLifeGraphTasks() {
        lifeGraphTaskBatchService.processPendingTasks();
    }

    @Scheduled(fixedDelay = 60000)
    public void recoverLifeGraphTasks() {
        lifeGraphTaskBatchService.recoverStaleTasks();
    }

    @Scheduled(fixedDelay = 3600000)
    public void cleanupLifeGraphTasks() {
        jobRunner.runIfLeader("lifegraph-cleanup", lifeGraphTaskBatchService::cleanupCompletedTasks);
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
}
