package com.aseubel.yusi.service.memory;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.dto.memory.LifeGraphMemoryItem;
import com.aseubel.yusi.pojo.dto.memory.LifeGraphMemoryResponse;
import com.aseubel.yusi.pojo.dto.memory.LifeGraphSourceItem;
import com.aseubel.yusi.pojo.dto.memory.UpdateLifeGraphMemoryRequest;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphMention;
import com.aseubel.yusi.pojo.entity.LifeGraphRelation;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.repository.LifeGraphEntityAliasRepository;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphMentionRepository;
import com.aseubel.yusi.repository.LifeGraphMergeJudgmentRepository;
import com.aseubel.yusi.repository.LifeGraphRelationEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.service.match.MatchProfileAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 关系图谱实体的透明度和生命周期操作。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LifeGraphLifecycleService {

    private static final int MAX_LIMIT = 100;
    private static final int MAX_SOURCES_PER_ENTITY = 20;

    private final LifeGraphEntityRepository entityRepository;
    private final LifeGraphEntityAliasRepository aliasRepository;
    private final LifeGraphMentionRepository mentionRepository;
    private final LifeGraphRelationRepository relationRepository;
    private final LifeGraphRelationEvidenceRepository evidenceRepository;
    private final LifeGraphMergeJudgmentRepository mergeJudgmentRepository;
    private final MatchProfileAssembler matchProfileAssembler;
    private final DiaryRepository diaryRepository;

    @Transactional(readOnly = true)
    public LifeGraphMemoryResponse list(String userId, int limit) {
        int safeLimit = Math.max(1, Math.min(MAX_LIMIT, limit));
        Page<LifeGraphEntity> page = entityRepository.findByUserId(
                userId, PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "mentionCount")));
        LocalDateTime now = LocalDateTime.now();
        List<LifeGraphMemoryItem> items = page.getContent().stream()
                .map(entity -> toItem(userId, entity, now))
                .toList();

        return LifeGraphMemoryResponse.builder()
                .entities(items)
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
    public LifeGraphMemoryItem update(String userId, Long entityId, UpdateLifeGraphMemoryRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "关系图谱实体修改内容不能为空");
        }
        LifeGraphEntity entity = findOwned(userId, entityId);
        boolean scopeChanged = false;

        if (request.getConfidence() != null) {
            entity.setConfidence(request.getConfidence());
        }
        if (request.getMatchAllowed() != null
                && !request.getMatchAllowed().equals(Boolean.TRUE.equals(entity.getMatchAllowed()))) {
            entity.setMatchAllowed(request.getMatchAllowed());
            scopeChanged = true;
        }
        if (request.getHidden() != null
                && !request.getHidden().equals(Boolean.TRUE.equals(entity.getHidden()))) {
            entity.setHidden(request.getHidden());
            scopeChanged = true;
        }
        if (Boolean.TRUE.equals(request.getClearValidUntil())) {
            if (entity.getValidUntil() != null) {
                entity.setValidUntil(null);
                scopeChanged = true;
            }
        } else if (request.getValidUntil() != null
                && !request.getValidUntil().equals(entity.getValidUntil())) {
            entity.setValidUntil(request.getValidUntil());
            scopeChanged = true;
        }

        LifeGraphEntity saved = entityRepository.save(entity);
        if (scopeChanged) {
            refreshMatchProfile(userId);
        }
        return toItem(userId, saved, LocalDateTime.now());
    }

    @Transactional
    public void delete(String userId, Long entityId) {
        LifeGraphEntity entity = findOwned(userId, entityId);
        List<LifeGraphRelation> sourceRelations = relationRepository
                .findByUserIdAndSourceIdIn(userId, List.of(entityId));
        List<LifeGraphRelation> targetRelations = relationRepository
                .findByUserIdAndTargetIdIn(userId, List.of(entityId));

        Map<Long, LifeGraphRelation> relations = new LinkedHashMap<>();
        sourceRelations.forEach(relation -> relations.put(relation.getId(), relation));
        targetRelations.forEach(relation -> relations.put(relation.getId(), relation));
        if (!relations.isEmpty()) {
            evidenceRepository.deleteByUserIdAndRelationIdIn(userId, new ArrayList<>(relations.keySet()));
            relationRepository.deleteAll(new ArrayList<>(relations.values()));
        }
        aliasRepository.deleteByUserIdAndEntityId(userId, entityId);
        mentionRepository.deleteByUserIdAndEntityId(userId, entityId);
        mergeJudgmentRepository.deleteByUserIdAndEntityId(userId, entityId);
        entityRepository.delete(entity);
        refreshMatchProfile(userId);
    }

    private LifeGraphEntity findOwned(String userId, Long entityId) {
        return entityRepository.findByIdAndUserId(entityId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "关系图谱实体不存在"));
    }

    private LifeGraphMemoryItem toItem(String userId, LifeGraphEntity entity, LocalDateTime now) {
        String lifecycleStatus;
        if (Boolean.TRUE.equals(entity.getHidden())) {
            lifecycleStatus = "HIDDEN";
        } else if (entity.getValidUntil() != null && !entity.getValidUntil().isAfter(now)) {
            lifecycleStatus = "EXPIRED";
        } else {
            lifecycleStatus = "ACTIVE";
        }

        List<LifeGraphSourceItem> sources = mentionRepository
                .findTop200ByUserIdAndEntityIdOrderByCreatedAtDesc(userId, entity.getId())
                .stream()
                .limit(MAX_SOURCES_PER_ENTITY)
                .map(mention -> toSource(userId, mention))
                .toList();

        return LifeGraphMemoryItem.builder()
                .id(entity.getId())
                .type(entity.getType() == null ? null : entity.getType().name())
                .displayName(entity.getDisplayName())
                .summary(entity.getSummary())
                .mentionCount(entity.getMentionCount())
                .relationCount(entity.getRelationCount())
                .confidence(entity.getConfidence() == null ? 0.5 : entity.getConfidence())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .validUntil(entity.getValidUntil())
                .matchAllowed(Boolean.TRUE.equals(entity.getMatchAllowed()))
                .hidden(Boolean.TRUE.equals(entity.getHidden()))
                .lifecycleStatus(lifecycleStatus)
                .sources(sources)
                .build();
    }

    private LifeGraphSourceItem toSource(String userId, LifeGraphMention mention) {
        String sourceTitle = null;
        if (mention.getDiaryId() != null) {
            Diary diary = diaryRepository.findByDiaryIdAndUserId(mention.getDiaryId(), userId);
            sourceTitle = diary == null ? null : diary.getTitle();
        }
        return LifeGraphSourceItem.builder()
                .sourceId(mention.getDiaryId())
                .sourceType("DIARY")
                .sourceTitle(sourceTitle)
                .entryDate(mention.getEntryDate())
                .createdAt(mention.getCreatedAt())
                .build();
    }

    private void refreshMatchProfile(String userId) {
        try {
            matchProfileAssembler.refreshProfile(userId);
        } catch (Exception exception) {
            log.warn("刷新匹配画像失败: userId={}", userId, exception);
        }
    }
}
