package com.aseubel.yusi.service.memory;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.dto.memory.MemoryCenterItem;
import com.aseubel.yusi.pojo.dto.memory.MemoryCenterResponse;
import com.aseubel.yusi.pojo.dto.memory.UpdateMemoryRequest;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.service.match.MatchProfileAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 记忆中心的透明度和生命周期操作。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MidTermMemoryLifecycleService {

    private final MidTermMemoryRepository memoryRepository;
    private final MidTermMemoryVectorService vectorService;
    private final MatchProfileAssembler matchProfileAssembler;
    private final DiaryRepository diaryRepository;

    @Transactional(readOnly = true)
    public MemoryCenterResponse list(String userId, int limit) {
        LocalDateTime now = LocalDateTime.now();
        List<MidTermMemory> memories = memoryRepository.findByUserIdOrderByCreatedAtDesc(
                userId, org.springframework.data.domain.PageRequest.of(0, limit));

        List<MemoryCenterItem> items = memories.stream().map(memory -> toItem(userId, memory, now)).toList();
        return MemoryCenterResponse.builder()
                .memories(items)
                .activeCount(items.stream().filter(item -> "ACTIVE".equals(item.getLifecycleStatus())).count())
                .hiddenCount(items.stream().filter(item -> "HIDDEN".equals(item.getLifecycleStatus())).count())
                .expiredCount(items.stream().filter(item -> "EXPIRED".equals(item.getLifecycleStatus())).count())
                .matchableCount(items.stream()
                        .filter(item -> "ACTIVE".equals(item.getLifecycleStatus())
                                && Boolean.TRUE.equals(item.getMatchAllowed()))
                        .count())
                .build();
    }

    @Transactional
    public MemoryCenterItem update(String userId, Long id, UpdateMemoryRequest request) {
        MidTermMemory memory = findOwned(userId, id);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "记忆修改内容不能为空");
        }

        boolean summaryChanged = false;
        boolean profileChanged = false;
        if (request.getSummary() != null) {
            String summary = request.getSummary().trim();
            if (summary.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "记忆摘要不能为空");
            }
            memory.setSummary(summary);
            summaryChanged = true;
        }
        if (request.getConfidence() != null) {
            memory.setConfidence(request.getConfidence());
        }
        if (request.getMatchAllowed() != null) {
            memory.setMatchAllowed(request.getMatchAllowed());
            profileChanged = true;
        }
        if (request.getHidden() != null) {
            memory.setHidden(request.getHidden());
            profileChanged = true;
        }
        if (Boolean.TRUE.equals(request.getClearValidUntil())) {
            memory.setValidUntil(null);
        } else if (request.getValidUntil() != null) {
            memory.setValidUntil(request.getValidUntil());
        }
        if (Boolean.TRUE.equals(request.getClearValidUntil()) || request.getValidUntil() != null) {
            profileChanged = true;
        }
        if (summaryChanged && Boolean.TRUE.equals(memory.getMatchAllowed())) {
            profileChanged = true;
        }
        memory.setUpdatedAt(LocalDateTime.now());
        MidTermMemory saved = memoryRepository.save(memory);

        if (summaryChanged || request.getMatchAllowed() != null || request.getHidden() != null) {
            syncVector(saved);
        }
        if (profileChanged) {
            refreshMatchProfile(userId);
        }
        return toItem(userId, saved, LocalDateTime.now());
    }

    @Transactional
    public void delete(String userId, Long id) {
        MidTermMemory memory = findOwned(userId, id);
        memoryRepository.delete(memory);
        try {
            vectorService.delete(memory.getId());
        } catch (Exception exception) {
            log.warn("删除中期记忆向量失败: memoryId={}", memory.getId(), exception);
        }
        refreshMatchProfile(userId);
    }

    private MidTermMemory findOwned(String userId, Long id) {
        return memoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "记忆不存在"));
    }

    private void syncVector(MidTermMemory memory) {
        try {
            vectorService.upsert(memory);
        } catch (Exception exception) {
            log.warn("同步中期记忆向量失败: memoryId={}", memory.getId(), exception);
        }
    }

    private void refreshMatchProfile(String userId) {
        try {
            matchProfileAssembler.refreshProfile(userId);
        } catch (Exception exception) {
            log.warn("刷新匹配画像失败: userId={}", userId, exception);
        }
    }

    private MemoryCenterItem toItem(String userId, MidTermMemory memory, LocalDateTime now) {
        String lifecycleStatus;
        if (Boolean.TRUE.equals(memory.getHidden())) {
            lifecycleStatus = "HIDDEN";
        } else if (memory.getValidUntil() != null && !memory.getValidUntil().isAfter(now)) {
            lifecycleStatus = "EXPIRED";
        } else if (memory.getMergedIntoId() != null) {
            lifecycleStatus = "MERGED";
        } else {
            lifecycleStatus = "ACTIVE";
        }

        String sourceType = StrUtil.blankToDefault(memory.getSourceType(), "UNKNOWN");
        String sourceTitle = null;
        if ("DIARY".equalsIgnoreCase(sourceType) && StrUtil.isNotBlank(memory.getSourceId())) {
            Diary diary = diaryRepository.findByDiaryIdAndUserId(memory.getSourceId(), userId);
            sourceTitle = diary == null ? null : diary.getTitle();
        }

        return MemoryCenterItem.builder()
                .id(memory.getId())
                .summary(memory.getSummary())
                .importance(memory.getImportance())
                .confidence(memory.getConfidence())
                .sourceType(sourceType)
                .sourceId(memory.getSourceId())
                .sourceTitle(sourceTitle)
                .createdAt(memory.getCreatedAt())
                .updatedAt(memory.getUpdatedAt())
                .validUntil(memory.getValidUntil())
                .mergedIntoId(memory.getMergedIntoId())
                .matchAllowed(Boolean.TRUE.equals(memory.getMatchAllowed()))
                .hidden(Boolean.TRUE.equals(memory.getHidden()))
                .lifecycleStatus(lifecycleStatus)
                .build();
    }
}
