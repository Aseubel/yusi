package com.aseubel.yusi.service.memory;

import com.aseubel.yusi.common.constant.LifecycleStatus;
import com.aseubel.yusi.common.constant.SourceType;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.constant.SecurityAuditAction;
import com.aseubel.yusi.pojo.constant.SecurityAuditActorType;
import com.aseubel.yusi.pojo.constant.SecurityAuditOutcome;
import com.aseubel.yusi.pojo.constant.SecurityAuditResourceType;
import com.aseubel.yusi.pojo.constant.SecurityAuditDetailKeys;
import com.aseubel.yusi.pojo.constant.SecurityAuditOperation;
import com.aseubel.yusi.pojo.dto.memory.LifeGraphMemoryItem;
import com.aseubel.yusi.pojo.dto.memory.LifeGraphMemoryResponse;
import com.aseubel.yusi.pojo.dto.memory.LifeGraphSourceItem;
import com.aseubel.yusi.pojo.dto.memory.UpdateLifeGraphMemoryRequest;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphEntityEvidence;
import com.aseubel.yusi.pojo.entity.LifeGraphMention;
import com.aseubel.yusi.pojo.entity.LifeGraphRelation;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.repository.LifeGraphEntityAliasRepository;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphEntityEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphMentionRepository;
import com.aseubel.yusi.repository.LifeGraphMergeJudgmentRepository;
import com.aseubel.yusi.repository.LifeGraphRelationEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.service.match.MatchProfileAssembler;
import com.aseubel.yusi.service.security.SecurityAuditCommand;
import com.aseubel.yusi.service.security.SecurityAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.aseubel.yusi.service.lifegraph.constant.LifeGraphConstants;
import com.aseubel.yusi.service.lifegraph.constant.LifeGraphRelationType;

/** 关系图谱实体的透明度和生命周期操作。 */
@Slf4j
@Service
public class LifeGraphLifecycleService {

    private static final int MAX_LIMIT = 100;
    private static final int MAX_SOURCES_PER_ENTITY = 20;

    private final LifeGraphEntityRepository entityRepository;
    private final LifeGraphEntityEvidenceRepository entityEvidenceRepository;
    private final LifeGraphEntityAliasRepository aliasRepository;
    private final LifeGraphMentionRepository mentionRepository;
    private final LifeGraphRelationRepository relationRepository;
    private final LifeGraphRelationEvidenceRepository evidenceRepository;
    private final LifeGraphMergeJudgmentRepository mergeJudgmentRepository;
    private final MatchProfileAssembler matchProfileAssembler;
    private final DiaryRepository diaryRepository;
    private final SecurityAuditService securityAuditService;

    public LifeGraphLifecycleService(LifeGraphEntityRepository entityRepository,
            LifeGraphEntityEvidenceRepository entityEvidenceRepository,
            LifeGraphEntityAliasRepository aliasRepository, LifeGraphMentionRepository mentionRepository,
            LifeGraphRelationRepository relationRepository, LifeGraphRelationEvidenceRepository evidenceRepository,
            LifeGraphMergeJudgmentRepository mergeJudgmentRepository, MatchProfileAssembler matchProfileAssembler,
            DiaryRepository diaryRepository) {
        this(entityRepository, entityEvidenceRepository, aliasRepository, mentionRepository, relationRepository,
                evidenceRepository, mergeJudgmentRepository, matchProfileAssembler, diaryRepository, null);
    }

    @Autowired
    public LifeGraphLifecycleService(LifeGraphEntityRepository entityRepository,
            LifeGraphEntityEvidenceRepository entityEvidenceRepository,
            LifeGraphEntityAliasRepository aliasRepository, LifeGraphMentionRepository mentionRepository,
            LifeGraphRelationRepository relationRepository, LifeGraphRelationEvidenceRepository evidenceRepository,
            LifeGraphMergeJudgmentRepository mergeJudgmentRepository, MatchProfileAssembler matchProfileAssembler,
            DiaryRepository diaryRepository, SecurityAuditService securityAuditService) {
        this.entityRepository = entityRepository;
        this.entityEvidenceRepository = entityEvidenceRepository;
        this.aliasRepository = aliasRepository;
        this.mentionRepository = mentionRepository;
        this.relationRepository = relationRepository;
        this.evidenceRepository = evidenceRepository;
        this.mergeJudgmentRepository = mergeJudgmentRepository;
        this.matchProfileAssembler = matchProfileAssembler;
        this.diaryRepository = diaryRepository;
        this.securityAuditService = securityAuditService;
    }

    @Transactional(readOnly = true)
    public LifeGraphMemoryResponse list(String userId, int limit) {
        int safeLimit = Math.max(1, Math.min(MAX_LIMIT, limit));
        Page<LifeGraphEntity> page = entityRepository.findByUserId(
                userId, PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "mentionCount")));
        LocalDateTime now = LocalDateTime.now();
        RelationProjectionContext relationContext = relationProjectionContext(userId, page.getContent());
        List<LifeGraphMemoryItem> items = page.getContent().stream()
                .map(entity -> toItem(userId, entity, now, relationContext))
                .toList();

        return LifeGraphMemoryResponse.builder()
                .entities(items)
                .activeCount(items.stream().filter(item -> LifecycleStatus.ACTIVE.code().equals(item.getLifecycleStatus())).count())
                .hiddenCount(items.stream().filter(item -> LifecycleStatus.HIDDEN.code().equals(item.getLifecycleStatus())).count())
                .expiredCount(items.stream().filter(item -> LifecycleStatus.EXPIRED.code().equals(item.getLifecycleStatus())).count())
                .matchableCount(items.stream()
                        .filter(item -> LifecycleStatus.ACTIVE.code().equals(item.getLifecycleStatus())
                                && Boolean.TRUE.equals(item.getMatchAllowed()))
                        .count())
                .build();
    }

    @Transactional
    public LifeGraphMemoryItem update(String userId, Long entityId, UpdateLifeGraphMemoryRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "关系图谱实体修改内容不能为空");
        }
        LifeGraphEntity entity = findOwned(userId, entityId, SecurityAuditOperation.UPDATE);
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
        recordAudit(SecurityAuditAction.LIFE_GRAPH_UPDATED, userId, saved.getId(),
                SecurityAuditOutcome.SUCCESS, SecurityAuditOperation.UPDATE);
        if (scopeChanged) {
            refreshMatchProfile(userId);
        }
        return toItem(userId, saved, LocalDateTime.now());
    }

    @Transactional
    public void delete(String userId, Long entityId) {
        LifeGraphEntity entity = findOwned(userId, entityId, SecurityAuditOperation.DELETE);
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
        recordAudit(SecurityAuditAction.LIFE_GRAPH_DELETED, userId, entityId,
                SecurityAuditOutcome.SUCCESS, SecurityAuditOperation.DELETE);
        refreshMatchProfile(userId);
    }

    private LifeGraphEntity findOwned(String userId, Long entityId, SecurityAuditOperation operation) {
        return entityRepository.findByIdAndUserId(entityId, userId)
                .orElseThrow(() -> {
                    recordAudit(SecurityAuditAction.ACCESS_DENIED, userId, entityId,
                            SecurityAuditOutcome.DENIED, operation);
                    return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "关系图谱实体不存在");
                });
    }

    private void recordAudit(SecurityAuditAction action, String userId, Long entityId,
            SecurityAuditOutcome outcome, SecurityAuditOperation operation) {
        if (securityAuditService == null) {
            return;
        }
        java.util.Map<String, String> details = operation == null
                ? java.util.Map.of()
                : java.util.Map.of(SecurityAuditDetailKeys.OPERATION, operation.name());
        securityAuditService.record(SecurityAuditCommand.builder()
                .action(action)
                .actorType(SecurityAuditActorType.USER)
                .actorUserId(userId)
                .subjectUserId(userId)
                .resourceType(SecurityAuditResourceType.LIFE_GRAPH_ENTITY)
                .resourceId(entityId == null ? null : String.valueOf(entityId))
                .outcome(outcome)
                .details(details)
                .scopeUserIds(java.util.Set.of(userId))
                .build());
    }

    private LifeGraphMemoryItem toItem(String userId, LifeGraphEntity entity, LocalDateTime now) {
        return toItem(userId, entity, now, relationProjectionContext(userId, List.of(entity)));
    }

    private LifeGraphMemoryItem toItem(String userId, LifeGraphEntity entity, LocalDateTime now,
            RelationProjectionContext relationContext) {
        String lifecycleStatus;
        if (Boolean.TRUE.equals(entity.getHidden())) {
            lifecycleStatus = LifecycleStatus.HIDDEN.code();
        } else if (entity.getValidUntil() != null && !entity.getValidUntil().isAfter(now)) {
            lifecycleStatus = LifecycleStatus.EXPIRED.code();
        } else {
            lifecycleStatus = LifecycleStatus.ACTIVE.code();
        }

        Map<String, LifeGraphSourceItem> sourceMap = new LinkedHashMap<>();
        for (LifeGraphEntityEvidence evidence : entityEvidenceRepository
                .findByUserIdAndEntityId(userId, entity.getId())) {
            sourceMap.put(sourceKey(evidence.getSourceType(), evidence.getSourceId()),
                    toSource(userId, evidence));
        }
        for (LifeGraphMention mention : mentionRepository
                .findTop200ByUserIdAndEntityIdOrderByCreatedAtDesc(userId, entity.getId())) {
            String key = sourceKey(SourceType.DIARY.code(), mention.getDiaryId());
            sourceMap.putIfAbsent(key, toSource(userId, mention));
        }
        List<LifeGraphSourceItem> sources = sourceMap.values().stream()
                .limit(MAX_SOURCES_PER_ENTITY)
                .toList();
        RelationProjection relation = relationContext.projectionFor(entity.getId());

        return LifeGraphMemoryItem.builder()
                .id(entity.getId())
                .type(entity.getType() == null ? null : entity.getType().name())
                .displayName(entity.getDisplayName())
                .summary(entity.getSummary())
                .mentionCount(entity.getMentionCount())
                .relationCount(entity.getRelationCount())
                .confidence(entity.getConfidence() == null ? 0.5 : entity.getConfidence())
                .importance(entity.getImportance() == null ? 0.5 : entity.getImportance())
                .relationToUser(relation == null ? null : relation.relationToUser())
                .relationOrigin(relation == null ? null : relation.relationOrigin())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .validUntil(entity.getValidUntil())
                .matchAllowed(Boolean.TRUE.equals(entity.getMatchAllowed()))
                .hidden(Boolean.TRUE.equals(entity.getHidden()))
                .lifecycleStatus(lifecycleStatus)
                .sources(sources)
                .build();
    }

    private RelationProjectionContext relationProjectionContext(String userId, List<LifeGraphEntity> entities) {
        Long userEntityId = entityRepository.findByUserIdAndTypeAndNameNorm(
                        userId, LifeGraphEntity.EntityType.User, LifeGraphConstants.USER_ENTITY_NORM)
                .map(LifeGraphEntity::getId)
                .orElse(null);
        if (userEntityId == null || entities == null || entities.isEmpty()) {
            return new RelationProjectionContext(Map.of());
        }

        Set<Long> personIds = entities.stream()
                .filter(entity -> entity != null && entity.getId() != null
                        && entity.getType() == LifeGraphEntity.EntityType.Person)
                .map(LifeGraphEntity::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (personIds.isEmpty()) {
            return new RelationProjectionContext(Map.of());
        }

        Map<Long, List<LifeGraphRelation>> candidatesByPerson = new LinkedHashMap<>();
        for (LifeGraphRelation relation : safeRelations(relationRepository.findByUserId(userId))) {
            Long personId = directPersonId(userId, userEntityId, personIds, relation);
            if (personId != null) {
                candidatesByPerson.computeIfAbsent(personId, ignored -> new ArrayList<>()).add(relation);
            }
        }

        Map<Long, RelationProjection> projections = new LinkedHashMap<>();
        candidatesByPerson.forEach((personId, candidates) -> {
            LifeGraphRelation selected = chooseRepresentativeRelation(candidates);
            if (selected == null) {
                return;
            }
            LifeGraphRelationType relationType = LifeGraphRelationType.fromCode(selected.getType());
            if (relationType == null || selected.getOrigin() == null) {
                return;
            }
            projections.put(personId, new RelationProjection(
                    relationType.code(), selected.getOrigin().name()));
        });
        return new RelationProjectionContext(projections);
    }

    private Long directPersonId(String userId, Long userEntityId, Set<Long> personIds,
            LifeGraphRelation relation) {
        if (relation == null || !Objects.equals(userId, relation.getUserId())
                || relation.getOrigin() == null) {
            return null;
        }
        LifeGraphRelationType relationType = LifeGraphRelationType.fromCode(relation.getType());
        if (relationType == null || !relationType.isPersonRelation()) {
            return null;
        }
        Long sourceId = semanticSourceId(relation);
        Long targetId = semanticTargetId(relation);
        if (Objects.equals(sourceId, userEntityId) && personIds.contains(targetId)) {
            return targetId;
        }
        if (Objects.equals(targetId, userEntityId) && personIds.contains(sourceId)) {
            return sourceId;
        }
        return null;
    }

    private LifeGraphRelation chooseRepresentativeRelation(List<LifeGraphRelation> candidates) {
        return candidates.stream()
                .sorted((left, right) -> {
                    int originComparison = Integer.compare(originPriority(right), originPriority(left));
                    if (originComparison != 0) {
                        return originComparison;
                    }
                    int updatedComparison = compareDescending(left.getUpdatedAt(), right.getUpdatedAt());
                    if (updatedComparison != 0) {
                        return updatedComparison;
                    }
                    int typeComparison = normalizedRelationType(left).compareTo(normalizedRelationType(right));
                    if (typeComparison != 0) {
                        return typeComparison;
                    }
                    return compareAscending(left.getId(), right.getId());
                })
                .findFirst()
                .orElse(null);
    }

    private int originPriority(LifeGraphRelation relation) {
        return relation.getOrigin() == LifeGraphRelation.Origin.MANUAL ? 1 : 0;
    }

    private String normalizedRelationType(LifeGraphRelation relation) {
        LifeGraphRelationType relationType = LifeGraphRelationType.fromCode(relation.getType());
        return relationType == null ? "" : relationType.code();
    }

    private int compareDescending(LocalDateTime left, LocalDateTime right) {
        if (Objects.equals(left, right)) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return right.compareTo(left);
    }

    private int compareAscending(Long left, Long right) {
        if (Objects.equals(left, right)) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    private Long semanticSourceId(LifeGraphRelation relation) {
        return relation.getSemanticSourceId() == null ? relation.getSourceId() : relation.getSemanticSourceId();
    }

    private Long semanticTargetId(LifeGraphRelation relation) {
        return relation.getSemanticTargetId() == null ? relation.getTargetId() : relation.getSemanticTargetId();
    }

    private List<LifeGraphRelation> safeRelations(List<LifeGraphRelation> relations) {
        return relations == null ? List.of() : relations;
    }

    private record RelationProjection(String relationToUser, String relationOrigin) {
    }

    private record RelationProjectionContext(Map<Long, RelationProjection> projections) {
        private RelationProjection projectionFor(Long entityId) {
            return projections.get(entityId);
        }
    }

    private LifeGraphSourceItem toSource(String userId, LifeGraphMention mention) {
        String sourceTitle = null;
        if (mention.getDiaryId() != null) {
            Diary diary = diaryRepository.findByDiaryIdAndUserId(mention.getDiaryId(), userId);
            sourceTitle = diary == null ? null : diary.getTitle();
        }
        return LifeGraphSourceItem.builder()
                .sourceId(mention.getDiaryId())
                .sourceType(SourceType.DIARY.code())
                .sourceTitle(sourceTitle)
                .entryDate(mention.getEntryDate())
                .createdAt(mention.getCreatedAt())
                .build();
    }

    private LifeGraphSourceItem toSource(String userId, LifeGraphEntityEvidence evidence) {
        String sourceType = evidence.getSourceType() == null
                ? SourceType.UNKNOWN.code() : evidence.getSourceType().toUpperCase();
        String sourceTitle = null;
        if (SourceType.DIARY.code().equals(sourceType)) {
            Diary diary = diaryRepository.findByDiaryIdAndUserId(evidence.getSourceId(), userId);
            sourceTitle = diary == null ? null : diary.getTitle();
        } else if (SourceType.PLAZA.code().equals(sourceType)) {
            sourceTitle = "广场卡片 #" + evidence.getSourceId();
        }
        return LifeGraphSourceItem.builder()
                .sourceId(evidence.getSourceId())
                .sourceType(sourceType)
                .sourceTitle(sourceTitle)
                .entryDate(evidence.getEntryDate())
                .createdAt(evidence.getCreatedAt())
                .build();
    }

    private String sourceKey(String sourceType, String sourceId) {
        return String.valueOf(sourceType) + ":" + String.valueOf(sourceId);
    }

    private void refreshMatchProfile(String userId) {
        try {
            matchProfileAssembler.refreshProfile(userId);
        } catch (Exception exception) {
            log.warn("刷新匹配画像失败: userId={}", userId, exception);
        }
    }
}
