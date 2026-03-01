package com.aseubel.yusi.common.task;

import com.aseubel.yusi.config.MemoryConfigProperties;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.ai.MemoryCompressionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 记忆系统相关的定时任务
 *
 * @author Aseubel
 * @date 2026/02/28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryScheduledTasks {

    private final UserRepository userRepository;
    private final MemoryCompressionService memoryCompressionService;
    private final MemoryConfigProperties memoryConfigProperties;

    /**
     * 定期扫描并执行中期记忆总结
     * 使用配置文件中的 Cron 表达式
     */
    @Scheduled(cron = "#{@memoryConfigProperties.midTermScanCron}")
    public void scanAndSummarizeMidTermMemory() {
        log.info("Starting mid-term memory scan task...");
        List<User> activeUsers = userRepository.findAll();

        for (User user : activeUsers) {
            try {
                memoryCompressionService.checkAndSummarizeMidTermMemory(user.getUserId());
            } catch (Exception e) {
                log.error("Error summarizing mid-term memory for user: {}", user.getUserId(), e);
            }
        }
        log.info("Finished mid-term memory scan task.");
    }

}
