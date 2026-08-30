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
     * 动态 System Message 的 token 预算。基础提示词和核心规则优先保留，
     * 低优先级的画像、冲突和中期记忆在预算不足时跳过。
     */
    private int contextTokenBudget = 4096;

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

    /** 半衰期衰减与遗忘配置 */
    private Decay decay = new Decay();

    /**
     * 衰减与遗忘参数。
     * 记忆不再按时间硬过期，而是按半衰期软衰减；
     * 只有"低初始重要性 + 衰减后低于阈值"的记忆才会被完全遗忘；
     * 检索命中会强化记忆并重置衰减时钟（被想起的记忆会巩固）。
     */
    @Data
    public static class Decay {

        /** 半衰期（天）：有效重要性每过 N 天减半 */
        private double halfLifeDays = 14.0;

        /** 衰减后有效重要性低于该值即视为完全遗忘 */
        private double forgottenThreshold = 0.1;

        /** 初始重要性低于该值的记忆才可能被完全遗忘（重要记忆永生） */
        private double initialImportanceGate = 0.5;

        /** 检索命中强化系数：new = old + (1 - old) * factor */
        private double reinforceFactor = 0.2;
    }

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
