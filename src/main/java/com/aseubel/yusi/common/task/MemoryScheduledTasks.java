package com.aseubel.yusi.common.task;

import com.aseubel.yusi.config.MemoryConfigProperties;
import com.aseubel.yusi.repository.ChatMemoryMessageRepository;
import com.aseubel.yusi.service.ai.MemoryCompressionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 记忆系统相关的定时任务（兜底扫描）
 * <p>
 * 主触发由事件驱动（{@link com.aseubel.yusi.common.event.MessageSavedEvent}）完成。
 * 本定时任务仅作兜底，处理未经事件覆盖的边界情况（如服务重启期间积累的消息）。
 * <p>
 * 只扫描有未总结消息的 memoryId，避免全用户表扫描。
 *
 * @author Aseubel
 * @date 2026/02/28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryScheduledTasks {

    private final ChatMemoryMessageRepository chatMemoryMessageRepository;
    private final MemoryCompressionService memoryCompressionService;
    private final MemoryConfigProperties memoryConfigProperties;

    /**
     * 定期兜底扫描并执行中期记忆总结
     * 使用配置文件中的 Cron 表达式（默认每 30 分钟，可通过 yusi.memory.mid-term-scan-cron 覆盖）
     */
    @Scheduled(cron = "#{@memoryConfigProperties.midTermScanCron}")
    public void scanAndSummarizeMidTermMemory() {
        log.info("Starting mid-term memory fallback scan...");

        // 只取有未总结消息的 memoryId，避免全量用户扫描
        List<String> activeMemoryIds = chatMemoryMessageRepository.findMemoryIdsWithUnsummarizedMessages();
        log.info("Found {} memoryIds with unsummarized messages", activeMemoryIds.size());

        for (String memoryId : activeMemoryIds) {
            try {
                memoryCompressionService.checkAndSummarizeMidTermMemory(memoryId);
            } catch (Exception e) {
                log.error("Error during fallback summarization for memoryId: {}", memoryId, e);
            }
        }

        log.info("Finished mid-term memory fallback scan.");
    }
}
