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
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(summary)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        midTermMemoryRepository.save(MidTermMemory.builder()
                .userId(userId)
                .summary(summary)
                .importance(importance != null ? importance : 0.5)
                .createdAt(now)
                .updatedAt(now)
                .validUntil(now.plusDays(30))
                .build());

        // F11.3: 异步检测新洞察是否与已有认知冲突
        CompletableFuture.runAsync(() -> conflictDetector.checkAndRecord(userId, summary));
    }
}
