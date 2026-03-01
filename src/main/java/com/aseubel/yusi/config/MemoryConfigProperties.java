package com.aseubel.yusi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 记忆系统配置属性
 *
 * @author Aseubel
 * @date 2026/03/01
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "yusi.memory")
public class MemoryConfigProperties {

    /**
     * 上下文记忆窗口大小（短期记忆的消息条数）
     * LangChain4j 提供的 MessageWindowChatMemory 会保留最近的 N 条消息
     */
    private int contextWindowSize = 50;

    /**
     * 中期记忆总结间隔（毫秒）
     * 用户最后一次对话后多久未总结则触发总结，默认 1 小时（3600000 毫秒）
     */
    private long midTermSummaryInterval = 3600000;

    /**
     * 中期记忆触发扫描的 Cron 表达式
     * 默认每 5 分钟执行一次扫描
     */
    private String midTermScanCron = "0 */5 * * * ?";
}
