package com.aseubel.yusi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

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
     * 事件驱动为主触发，定时任务作兜底，默认每 30 分钟执行一次
     */
    private String midTermScanCron = "0 */30 * * * ?";

    /**
     * 以 Duration 形式返回中期记忆总结间隔，避免调用方手工换算毫秒
     */
    public Duration getMidTermSummaryIntervalDuration() {
        return Duration.ofMillis(midTermSummaryInterval);
    }

    /**
     * 消息量硬上限（达到此数量时不等冷却期直接触发压缩）
     * 默认为上下文窗口大小的 2 倍
     */
    public int getHardLimitSize() {
        return contextWindowSize * 2;
    }
}
