package com.aseubel.yusi.common.task;

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

    /**
     * 每天凌晨 3 点执行记忆压缩任务
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void compressMemoriesDaily() {
        log.info("Starting daily memory compression task...");
        List<User> activeUsers = userRepository.findAll(); // 简单起见，遍历所有用户。实际生产中可能需要只查近期活跃用户

        for (User user : activeUsers) {
            try {
                // 读取最近 50 条消息进行提取，这可以作为一个配置项
                memoryCompressionService.compressRecentMemory(user.getUserId(), 50);
            } catch (Exception e) {
                log.error("Error compressing memory for user: {}", user.getUserId(), e);
            }
        }
        log.info("Finished daily memory compression task.");
    }
}
