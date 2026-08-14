package com.aseubel.yusi.service.memory.impl;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.constant.SourceType;
import com.aseubel.yusi.pojo.constant.MidMemoryCategory;
import com.aseubel.yusi.pojo.constant.SecurityAuditAction;
import com.aseubel.yusi.pojo.constant.SecurityAuditActorType;
import com.aseubel.yusi.pojo.constant.SecurityAuditDetailKeys;
import com.aseubel.yusi.pojo.constant.SecurityAuditOutcome;
import com.aseubel.yusi.pojo.constant.SecurityAuditResourceType;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.service.cognition.CognitiveConflictDetector;
import com.aseubel.yusi.service.memory.MidMemoryUpdateService;
import com.aseubel.yusi.service.memory.MidTermMemoryVectorService;
import com.aseubel.yusi.service.security.SecurityAuditCommand;
import com.aseubel.yusi.service.security.SecurityAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class MidMemoryUpdateServiceImpl implements MidMemoryUpdateService {

    private final MidTermMemoryRepository midTermMemoryRepository;
    private final CognitiveConflictDetector conflictDetector;
    private final ThreadPoolTaskExecutor threadPoolExecutor;
    private final MidTermMemoryVectorService vectorService;
    private final SecurityAuditService securityAuditService;

    public MidMemoryUpdateServiceImpl(MidTermMemoryRepository midTermMemoryRepository,
            CognitiveConflictDetector conflictDetector, ThreadPoolTaskExecutor threadPoolExecutor,
            MidTermMemoryVectorService vectorService) {
        this(midTermMemoryRepository, conflictDetector, threadPoolExecutor, vectorService, null);
    }

    @Autowired
    public MidMemoryUpdateServiceImpl(MidTermMemoryRepository midTermMemoryRepository,
            CognitiveConflictDetector conflictDetector, ThreadPoolTaskExecutor threadPoolExecutor,
            MidTermMemoryVectorService vectorService, SecurityAuditService securityAuditService) {
        this.midTermMemoryRepository = midTermMemoryRepository;
        this.conflictDetector = conflictDetector;
        this.threadPoolExecutor = threadPoolExecutor;
        this.vectorService = vectorService;
        this.securityAuditService = securityAuditService;
    }

    @Override
    @Transactional
    public void removeBySource(String userId, String sourceType, String sourceId) {
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(sourceType) || StrUtil.isBlank(sourceId)) {
            return;
        }
        List<MidTermMemory> memories = midTermMemoryRepository
                .findByUserIdAndSourceTypeAndSourceId(userId, sourceType, sourceId);
        for (MidTermMemory memory : memories) {
            midTermMemoryRepository.delete(memory);
            recordAudit(SecurityAuditAction.MEMORY_DELETED, userId, memory.getId(),
                    sourceType, SecurityAuditOutcome.SUCCESS);
            try {
                vectorService.delete(memory.getId());
            } catch (Exception exception) {
                log.warn("删除来源记忆向量失败: userId={}, sourceType={}, sourceId={}, memoryId={}",
                        userId, sourceType, sourceId, memory.getId(), exception);
            }
        }
    }

    @Override
    @Transactional
    public void appendSnapshot(String userId, String summary, Double importance) {
        appendSnapshot(userId, summary, importance, MidMemoryCategory.EVENT_OR_PLAN.code());
    }

    @Override
    @Transactional
    public void appendSnapshot(String userId, String summary, Double importance, String category) {
        appendSnapshot(userId, summary, importance, category, SourceType.UNKNOWN.code(), null);
    }

    @Override
    @Transactional
    public void appendSnapshot(String userId, String summary, Double importance, String category,
                               String sourceType, String sourceId) {
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(summary)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime validUntil;
        if (MidMemoryCategory.EMOTION_OR_STATE.code().equalsIgnoreCase(category)) {
            validUntil = now.plusDays(14);
        } else if (MidMemoryCategory.PREFERENCE_OR_HABIT.code().equalsIgnoreCase(category)) {
            validUntil = now.plusDays(180);
        } else {
            validUntil = now.plusDays(30); // Default for EVENT_OR_PLAN and others
        }

        MidTermMemory saved = midTermMemoryRepository.save(MidTermMemory.builder()
                .userId(userId)
                .sourceType(StrUtil.blankToDefault(sourceType, SourceType.UNKNOWN.code()))
                .sourceId(StrUtil.isBlank(sourceId) ? null : sourceId.trim())
                .summary(summary)
                .importance(importance != null ? importance : 0.5)
                .confidence(normalize(importance))
                .matchAllowed(false)
                .hidden(false)
                .createdAt(now)
                .updatedAt(now)
                .validUntil(validUntil)
                .build());
        recordAudit(SecurityAuditAction.MEMORY_CREATED, userId, saved == null ? null : saved.getId(),
                sourceType, SecurityAuditOutcome.SUCCESS);

        // F11.3: 异步检测新洞察是否与已有认知冲突
        CompletableFuture.runAsync(() -> conflictDetector.checkAndRecord(userId, summary), threadPoolExecutor);
    }

    private double normalize(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return 0.5;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private void recordAudit(SecurityAuditAction action, String userId, Long memoryId,
            String sourceType, SecurityAuditOutcome outcome) {
        if (securityAuditService == null) {
            return;
        }
        securityAuditService.record(SecurityAuditCommand.builder()
                .action(action)
                .actorType(SecurityAuditActorType.SYSTEM)
                .subjectUserId(userId)
                .resourceType(SecurityAuditResourceType.MID_TERM_MEMORY)
                .resourceId(memoryId == null ? null : String.valueOf(memoryId))
                .outcome(outcome)
                .details(java.util.Map.of(SecurityAuditDetailKeys.SOURCE_TYPE,
                        StrUtil.blankToDefault(sourceType, SourceType.UNKNOWN.code())))
                .scopeUserIds(java.util.Set.of(userId))
                .build());
    }
}
