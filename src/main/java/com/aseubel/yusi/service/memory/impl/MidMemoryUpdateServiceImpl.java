package com.aseubel.yusi.service.memory.impl;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.service.cognition.CognitiveConflictDetector;
import com.aseubel.yusi.service.memory.MidMemoryUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class MidMemoryUpdateServiceImpl implements MidMemoryUpdateService {

    private final MidTermMemoryRepository midTermMemoryRepository;
    private final CognitiveConflictDetector conflictDetector;

    @Override
    @Transactional
    public void appendSnapshot(String userId, String summary, Double importance) {
        appendSnapshot(userId, summary, importance, "EVENT_OR_PLAN");
    }

    @Override
    @Transactional
    public void appendSnapshot(String userId, String summary, Double importance, String category) {
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(summary)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime validUntil;
        if ("EMOTION_OR_STATE".equalsIgnoreCase(category)) {
            validUntil = now.plusDays(14);
        } else if ("PREFERENCE_OR_HABIT".equalsIgnoreCase(category)) {
            validUntil = now.plusDays(180);
        } else {
            validUntil = now.plusDays(30); // Default for EVENT_OR_PLAN and others
        }

        midTermMemoryRepository.save(MidTermMemory.builder()
                .userId(userId)
                .summary(summary)
                .importance(importance != null ? importance : 0.5)
                .createdAt(now)
                .updatedAt(now)
                .validUntil(validUntil)
                .build());

        // F11.3: 异步检测新洞察是否与已有认知冲突
        CompletableFuture.runAsync(() -> conflictDetector.checkAndRecord(userId, summary));
    }
}
