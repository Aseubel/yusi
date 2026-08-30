package com.aseubel.yusi.service.memory;

import com.aseubel.yusi.config.MemoryConfigProperties;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 中期记忆的半衰期衰减与遗忘服务。
 * 记忆不按时间硬过期，而是按半衰期软衰减；衰减只影响排序与注入权重，
 * 只有"初始重要性低于门槛 + 衰减后低于阈值"的低价值记忆才会被完全遗忘（懒判定落库）。
 * 检索命中会强化记忆并重置衰减时钟——被想起的记忆会巩固。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryDecayService {

    private final MemoryConfigProperties memoryConfigProperties;
    private final MidTermMemoryRepository memoryRepository;

    /**
     * 有效重要性 = importance × 0.5^(距衰减基准的天数 / 半衰期)。
     * 衰减基准为最后一次命中强化时间（无则退化为创建时间）。
     */
    public double effectiveImportance(MidTermMemory memory) {
        double importance = memory.getImportance() != null ? memory.getImportance() : 0.5;
        LocalDateTime base = memory.getLastReinforcedAt() != null
                ? memory.getLastReinforcedAt() : memory.getCreatedAt();
        if (base == null) {
            return importance;
        }
        long days = ChronoUnit.DAYS.between(base, LocalDateTime.now());
        if (days <= 0) {
            return importance;
        }
        double halfLife = memoryConfigProperties.getDecay().getHalfLifeDays();
        return importance * Math.pow(0.5, (double) days / halfLife);
    }

    /**
     * 双重门槛完全遗忘判定：初始重要性低于门槛，且衰减后有效重要性低于阈值。
     * 已落库标记的（forgottenAt 非空）直接视为已遗忘。
     */
    public boolean isForgotten(MidTermMemory memory) {
        if (memory.getForgottenAt() != null) {
            return true;
        }
        Double initial = memory.getInitialImportance();
        if (initial == null || initial >= memoryConfigProperties.getDecay().getInitialImportanceGate()) {
            return false;
        }
        return effectiveImportance(memory) < memoryConfigProperties.getDecay().getForgottenThreshold();
    }

    /**
     * 懒遗忘判定：消费时发现满足遗忘条件则落库标记并过滤，无需定时任务。
     * 返回 true 表示该记忆应从消费结果中过滤。
     */
    public boolean checkAndMarkForgotten(MidTermMemory memory) {
        if (!isForgotten(memory)) {
            return false;
        }
        if (memory.getForgottenAt() == null) {
            memory.setForgottenAt(LocalDateTime.now());
            memory.setUpdatedAt(LocalDateTime.now());
            memoryRepository.save(memory);
            log.info("Mid-term memory forgotten (lazy): memoryId={}, initialImportance={}",
                    memory.getId(), memory.getInitialImportance());
        }
        return true;
    }

    /**
     * 检索命中强化：先把当前有效重要性结算落库（巩固衰减），再按系数向 1 逼近，并重置衰减时钟。
     * 公式：new = old + (1 - old) × reinforceFactor。
     */
    public void reinforce(MidTermMemory memory) {
        double factor = memoryConfigProperties.getDecay().getReinforceFactor();
        double effective = effectiveImportance(memory);
        double reinforced = effective + (1.0 - effective) * factor;
        memory.setImportance(Math.min(1.0, reinforced));
        memory.setLastReinforcedAt(LocalDateTime.now());
        memory.setUpdatedAt(LocalDateTime.now());
        memoryRepository.save(memory);
    }
}
