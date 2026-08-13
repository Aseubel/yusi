package com.aseubel.yusi.service.lifegraph.impl;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.common.constant.SourceType;
import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphEntityAlias;
import com.aseubel.yusi.pojo.entity.LifeGraphEntityEvidence;
import com.aseubel.yusi.pojo.entity.LifeGraphMention;
import com.aseubel.yusi.pojo.entity.LifeGraphRelation;
import com.aseubel.yusi.pojo.entity.LifeGraphRelationEvidence;
import com.aseubel.yusi.repository.LifeGraphEntityAliasRepository;
import com.aseubel.yusi.repository.LifeGraphEntityEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphMentionRepository;
import com.aseubel.yusi.repository.LifeGraphRelationEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.lifegraph.LifeGraphBuildService;
import com.aseubel.yusi.service.lifegraph.LifeGraphPromotionPolicy;
import com.aseubel.yusi.service.lifegraph.ai.LifeGraphExtractor;
import com.aseubel.yusi.service.lifegraph.constant.LifeGraphConstants;
import com.aseubel.yusi.service.lifegraph.constant.LifeGraphEvidenceKind;
import com.aseubel.yusi.service.lifegraph.dto.LifeGraphExtractionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LifeGraphBuildServiceImpl implements LifeGraphBuildService {

    private final LifeGraphEntityRepository entityRepository;
    private final LifeGraphEntityAliasRepository aliasRepository;
    private final LifeGraphRelationRepository relationRepository;
    private final LifeGraphRelationEvidenceRepository relationEvidenceRepository;
    private final LifeGraphMentionRepository mentionRepository;
    private final LifeGraphEntityEvidenceRepository entityEvidenceRepository;
    private final PromptManager promptManager;
    private final LifeGraphExtractor extractor;
    private final ObjectMapper objectMapper;
    private final LifeGraphPromotionPolicy promotionPolicy;
    private final PlatformTransactionManager transactionManager;

    @Autowired
    public LifeGraphBuildServiceImpl(
            LifeGraphEntityRepository entityRepository,
            LifeGraphEntityAliasRepository aliasRepository,
            LifeGraphRelationRepository relationRepository,
            LifeGraphRelationEvidenceRepository relationEvidenceRepository,
            LifeGraphMentionRepository mentionRepository,
            LifeGraphEntityEvidenceRepository entityEvidenceRepository,
            PromptManager promptManager,
            LifeGraphExtractor extractor,
            ObjectMapper objectMapper,
            LifeGraphPromotionPolicy promotionPolicy,
            PlatformTransactionManager transactionManager) {
        this.entityRepository = entityRepository;
        this.aliasRepository = aliasRepository;
        this.relationRepository = relationRepository;
        this.relationEvidenceRepository = relationEvidenceRepository;
        this.mentionRepository = mentionRepository;
        this.entityEvidenceRepository = entityEvidenceRepository;
        this.promptManager = promptManager;
        this.extractor = extractor;
        this.objectMapper = objectMapper;
        this.promotionPolicy = promotionPolicy;
        this.transactionManager = transactionManager;
    }

    /**
     * Compatibility constructor for focused unit tests and old integrations.
     */
    public LifeGraphBuildServiceImpl(
            LifeGraphEntityRepository entityRepository,
            LifeGraphEntityAliasRepository aliasRepository,
            LifeGraphRelationRepository relationRepository,
            LifeGraphRelationEvidenceRepository relationEvidenceRepository,
            LifeGraphMentionRepository mentionRepository,
            PromptManager promptManager,
            LifeGraphExtractor extractor,
            ObjectMapper objectMapper) {
        this(entityRepository, aliasRepository, relationRepository, relationEvidenceRepository, mentionRepository,
                null, promptManager, extractor, objectMapper, new LifeGraphPromotionPolicy(), null);
    }

    @Override
    public void upsertFromDiary(Diary diary, String plainContent) {
        if (diary == null || StrUtil.isBlank(diary.getUserId()) || StrUtil.isBlank(diary.getDiaryId())) {
            return;
        }
        if (StrUtil.isBlank(plainContent)) {
            deleteBySource(diary.getUserId(), SourceType.DIARY.code(), diary.getDiaryId());
            return;
        }

        LifeGraphExtractionResult extraction = extract(
                diary.getUserId(),
                diary.getEntryDate() == null ? null : diary.getEntryDate().toString(),
                diary.getTitle(),
                diary.getPlaceName(),
                diary.getAddress(),
                coordinates(diary.getLatitude(), diary.getLongitude()),
                plainContent);
        replaceAfterSuccessfulExtraction(sourceFromDiary(diary, plainContent), extraction);
    }

    @Override
    public void upsertFromPlaza(CognitionIngestCommand command) {
        if (command == null || StrUtil.isBlank(command.getUserId()) || StrUtil.isBlank(command.getSourceId())) {
            return;
        }
        if (StrUtil.isBlank(command.getMaskedText())) {
            deleteBySource(command.getUserId(), SourceType.PLAZA.code(), command.getSourceId());
            return;
        }

        LocalDate entryDate = command.getTimestamp() == null ? null : command.getTimestamp().toLocalDate();
        LifeGraphExtractionResult extraction = extract(
                command.getUserId(),
                entryDate == null ? null : entryDate.toString(),
                command.getTitle(),
                command.getPlaceName(),
                null,
                null,
                command.getMaskedText());
        replaceAfterSuccessfulExtraction(sourceFromPlaza(command), extraction);
    }

    @Override
    public void deleteByDiary(String userId, String diaryId) {
        deleteBySource(userId, SourceType.DIARY.code(), diaryId);
    }

    @Override
    public void deleteBySource(String userId, String sourceType, String sourceId) {
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(sourceType) || StrUtil.isBlank(sourceId)) {
            return;
        }
        runInTransaction(() -> deleteSourceInternal(userId, sourceType.trim().toUpperCase(Locale.ROOT), sourceId));
    }

    private void replaceAfterSuccessfulExtraction(SourceContext source,
                                                  LifeGraphExtractionResult extraction) {
        if (extraction == null) {
            throw new IllegalStateException("GraphRAG extraction returned invalid JSON");
        }
        Set<String> confirmedPersons = findConfirmedImportantPersons(source.userId());
        LifeGraphPromotionPolicy.PromotionResult promotion = promotionPolicy.promote(extraction, confirmedPersons);
        runInTransaction(() -> writeReplacement(source, extraction, promotion));
    }

    private LifeGraphExtractionResult extract(String userId, String entryDate, String title,
                                              String placeName, String address, String coordinates,
                                              String plainContent) {
        String prompt = promptManager.getPrompt(PromptKey.GRAPHRAG_EXTRACT);
        String raw;
        try {
            ModelRouteContextHolder.set(ModelRouteContext.builder()
                    .scene(PromptKey.GRAPHRAG_EXTRACT.getKey())
                    .build());
            raw = extractor.extract(prompt, buildKnownEntities(userId),
                    StrUtil.blankToDefault(entryDate, ""),
                    StrUtil.blankToDefault(title, ""),
                    StrUtil.blankToDefault(placeName, ""),
                    StrUtil.blankToDefault(address, ""),
                    StrUtil.blankToDefault(coordinates, ""),
                    plainContent);
        } finally {
            ModelRouteContextHolder.clear();
        }
        LifeGraphExtractionResult parsed = parseExtractionResult(raw);
        if (parsed == null) {
            throw new IllegalStateException("GraphRAG extraction returned invalid JSON");
        }
        return parsed;
    }

    private void writeReplacement(SourceContext source,
                                  LifeGraphExtractionResult extraction,
                                  LifeGraphPromotionPolicy.PromotionResult promotion) {
        deleteSourceInternal(source.userId(), source.sourceType(), source.sourceId());
        ensureUserEntity(source.userId());

        Map<String, Long> resolvedEntityIds = new HashMap<>();
        for (LifeGraphExtractionResult.ExtractedEntity extracted : promotion.entities()) {
            String key = promotionPolicy.normalizeKey(extracted.getNameNorm(), extracted.getDisplayName());
            Long entityId = resolveAndUpsertEntity(source, extracted);
            if (entityId != null && key != null) {
                resolvedEntityIds.put(key, entityId);
            }
        }
        resolvedEntityIds.put(LifeGraphConstants.USER_ENTITY_NORM,
                findUserEntityId(source.userId()));

        Map<String, LifeGraphExtractionResult.ExtractedMention> mentionByEntity = new HashMap<>();
        if (extraction.getMentions() != null) {
            for (LifeGraphExtractionResult.ExtractedMention mention : extraction.getMentions()) {
                if (mention == null) {
                    continue;
                }
                String key = promotionPolicy.normalizeKey(mention.getEntity(), null);
                if (key != null) {
                    mentionByEntity.putIfAbsent(key, mention);
                }
            }
        }

        Set<Long> contributedEntityIds = new LinkedHashSet<>();
        for (LifeGraphExtractionResult.ExtractedEntity extracted : promotion.entities()) {
            String key = promotionPolicy.normalizeKey(extracted.getNameNorm(), extracted.getDisplayName());
            Long entityId = resolvedEntityIds.get(key);
            if (entityId == null) {
                continue;
            }
            LifeGraphExtractionResult.ExtractedMention mention = mentionByEntity.get(key);
            saveEntityEvidence(source, entityId, extracted, mention,
                    evidenceKind(source.sourceType(), key, promotion));
            contributedEntityIds.add(entityId);
            if (SourceType.DIARY.code().equals(source.sourceType()) && mention != null) {
                ensureDiaryMention(source, entityId, mention);
            }
        }

        Map<String, Integer> occurrenceCounts = promotion.relationOccurrences();
        for (LifeGraphExtractionResult.ExtractedRelation extracted : promotion.relations()) {
            String sourceKey = promotionPolicy.normalizeKey(extracted.getSource(), null);
            String targetKey = promotionPolicy.normalizeKey(extracted.getTarget(), null);
            Long sourceId = resolvedEntityIds.get(sourceKey);
            Long targetId = resolvedEntityIds.get(targetKey);
            if (sourceId == null || targetId == null || Objects.equals(sourceId, targetId)) {
                continue;
            }
            String type = extracted.getType().trim().toUpperCase(Locale.ROOT);
            String occurrenceKey = sourceKey + "|" + targetKey + "|" + type;
            upsertRelation(source, extracted, sourceId, targetId,
                    occurrenceCounts.getOrDefault(occurrenceKey, 1));
        }

        for (Long entityId : contributedEntityIds) {
            refreshEntityAggregate(source.userId(), entityId);
        }
    }

    private void deleteSourceInternal(String userId, String sourceType, String sourceId) {
        Set<Long> affectedEntityIds = new LinkedHashSet<>();
        if (SourceType.DIARY.code().equals(sourceType)) {
            List<LifeGraphMention> mentions = safeList(mentionRepository.findByUserIdAndDiaryId(userId, sourceId));
            affectedEntityIds.addAll(mentions.stream()
                    .map(LifeGraphMention::getEntityId)
                    .filter(Objects::nonNull)
                    .toList());
            mentionRepository.deleteByUserIdAndDiaryId(userId, sourceId);
        }
        if (entityEvidenceRepository != null) {
            List<LifeGraphEntityEvidence> evidences = safeList(
                    entityEvidenceRepository.findByUserIdAndSourceTypeAndSourceId(userId, sourceType, sourceId));
            affectedEntityIds.addAll(evidences.stream()
                    .map(LifeGraphEntityEvidence::getEntityId)
                    .filter(Objects::nonNull)
                    .toList());
        }

        List<LifeGraphRelationEvidence> sourceEvidence = safeList(
                relationEvidenceRepository.findByUserIdAndSourceTypeAndSourceId(userId, sourceType, sourceId));
        Set<Long> relationIds = sourceEvidence.stream()
                .map(LifeGraphRelationEvidence::getRelationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Long relationId : relationIds) {
            recalculateRelationAfterSourceRemoval(userId, relationId, sourceType, sourceId);
        }
        relationEvidenceRepository.deleteByUserIdAndSourceTypeAndSourceId(userId, sourceType, sourceId);

        if (entityEvidenceRepository != null) {
            entityEvidenceRepository.deleteByUserIdAndSourceTypeAndSourceId(userId, sourceType, sourceId);
        }
        for (Long entityId : affectedEntityIds) {
            refreshEntityAggregate(userId, entityId);
        }
    }

    private void recalculateRelationAfterSourceRemoval(String userId, Long relationId,
                                                       String sourceType, String sourceId) {
        Optional<LifeGraphRelation> relationOptional = relationRepository.findByIdAndUserId(relationId, userId);
        if (relationOptional.isEmpty()) {
            return;
        }
        LifeGraphRelation relation = relationOptional.get();
        List<LifeGraphRelationEvidence> allEvidence = safeList(
                relationEvidenceRepository.findByUserIdAndRelationId(userId, relationId));
        List<LifeGraphRelationEvidence> remaining = allEvidence.stream()
                .filter(evidence -> !sourceType.equals(evidence.getSourceType())
                        || !sourceId.equals(evidence.getSourceId()))
                .toList();
        int automaticWeight = remaining.stream()
                .mapToInt(evidence -> safeOccurrenceCount(evidence.getOccurrenceCount()))
                .sum();

        if (relation.getOrigin() == LifeGraphRelation.Origin.AUTO && remaining.isEmpty()) {
            relationEvidenceRepository.deleteByUserIdAndRelationId(userId, relationId);
            relationRepository.delete(relation);
            return;
        }

        if (relation.getOrigin() == LifeGraphRelation.Origin.AUTO) {
            relation.setWeight(automaticWeight);
        } else {
            int manualWeight = relation.getManualWeight() == null
                    ? Math.max(0, relation.getWeight() == null ? 0 : relation.getWeight())
                    : Math.max(0, relation.getManualWeight());
            relation.setManualWeight(manualWeight);
            relation.setWeight(manualWeight + automaticWeight);
        }
        relation.setEvidenceDiaryId(remaining.stream()
                .filter(evidence -> SourceType.DIARY.code().equals(evidence.getSourceType()))
                .max(Comparator.comparing(this::evidenceTime))
                .map(LifeGraphRelationEvidence::getSourceId)
                .orElse(null));
        relationRepository.save(relation);
    }

    private void refreshEntityAggregate(String userId, Long entityId) {
        Optional<LifeGraphEntity> optional = entityRepository.findByIdAndUserId(entityId, userId);
        if (optional.isEmpty()) {
            return;
        }
        LifeGraphEntity entity = optional.get();
        Set<String> sourceKeys = new HashSet<>();
        if (entityEvidenceRepository != null) {
            for (LifeGraphEntityEvidence evidence : safeList(
                    entityEvidenceRepository.findByUserIdAndEntityId(userId, entityId))) {
                sourceKeys.add(evidence.getSourceType() + ":" + evidence.getSourceId());
            }
        }
        for (LifeGraphMention mention : safeList(mentionRepository.findByUserIdAndEntityId(userId, entityId))) {
            if (mention.getDiaryId() != null) {
                sourceKeys.add(SourceType.DIARY.code() + ":" + mention.getDiaryId());
            }
        }
        if (entity.getOrigin() == LifeGraphEntity.Origin.AUTO
                && entity.getType() != LifeGraphEntity.EntityType.User
                && !LifeGraphConstants.USER_ENTITY_NORM.equalsIgnoreCase(entity.getNameNorm())
                && sourceKeys.isEmpty()) {
            aliasRepository.deleteByUserIdAndEntityId(userId, entityId);
            entityRepository.delete(entity);
            return;
        }
        if (entity.getOrigin() == LifeGraphEntity.Origin.AUTO) {
            entity.setMentionCount(sourceKeys.size());
            entity.setLastMentionAt(LocalDateTime.now());
            entityRepository.save(entity);
        }
    }

    private Long resolveAndUpsertEntity(SourceContext source,
                                        LifeGraphExtractionResult.ExtractedEntity extracted) {
        String displayName = StrUtil.blankToDefault(extracted.getDisplayName(), "");
        String nameNorm = promotionPolicy.normalizeKey(extracted.getNameNorm(), displayName);
        if (StrUtil.isBlank(nameNorm) || LifeGraphConstants.USER_ENTITY_NORM.equals(nameNorm)) {
            return null;
        }
        LifeGraphEntity.EntityType type = parseType(extracted.getType());
        if (type == null || type == LifeGraphEntity.EntityType.User) {
            return null;
        }

        LifeGraphEntity entity = resolveEntityByAliasOrNorm(source.userId(), type, nameNorm);
        if (entity == null) {
            entity = LifeGraphEntity.builder()
                    .userId(source.userId())
                    .type(type)
                    .nameNorm(nameNorm)
                    .displayName(StrUtil.isBlank(displayName) ? nameNorm : displayName)
                    .mentionCount(0)
                    .relationCount(0)
                    .confidence(toDoubleConfidence(extracted.getConfidence()))
                    .importance(clampImportance(extracted.getImportance()))
                    .matchAllowed(false)
                    .hidden(false)
                    .origin(LifeGraphEntity.Origin.AUTO)
                    .firstMentionDate(source.entryDate())
                    .build();
        }

        if (StrUtil.isNotBlank(extracted.getSummary()) && StrUtil.isBlank(entity.getSummary())) {
            entity.setSummary(extracted.getSummary());
        }
        if (extracted.getImportance() != null) {
            entity.setImportance(clampImportance(extracted.getImportance()));
        }
        entity.setLastMentionAt(source.sourceTime() == null ? LocalDateTime.now() : source.sourceTime());
        if (entity.getFirstMentionDate() == null) {
            entity.setFirstMentionDate(source.entryDate());
        }

        String props = mergeProps(entity.getProps(), extracted.getProps());
        if (StrUtil.isNotBlank(extracted.getEmotion())) {
            props = mergeProps(props, Map.of("emotion", extracted.getEmotion()));
        }
        if (source.diary() != null && type == LifeGraphEntity.EntityType.Place
                && source.diary().getLatitude() != null && source.diary().getLongitude() != null) {
            Map<String, Object> coordinates = new HashMap<>();
            coordinates.put("lat", source.diary().getLatitude());
            coordinates.put("lng", source.diary().getLongitude());
            Map<String, Object> geo = new HashMap<>();
            geo.put("coordinates", coordinates);
            geo.put("address", source.diary().getAddress());
            geo.put("placeId", source.diary().getPlaceId());
            props = mergeProps(props, geo);
        }
        entity.setProps(props);

        LifeGraphEntity saved = entityRepository.save(entity);
        upsertAlias(source.userId(), saved.getId(), saved.getDisplayName(), extracted.getConfidence());
        if (extracted.getAliases() != null) {
            for (String alias : extracted.getAliases()) {
                upsertAlias(source.userId(), saved.getId(), alias, extracted.getConfidence());
            }
        }
        return saved.getId();
    }

    private void upsertRelation(SourceContext source,
                                LifeGraphExtractionResult.ExtractedRelation extracted,
                                Long semanticSourceId, Long semanticTargetId,
                                int occurrenceCount) {
        long physicalSourceId = Math.min(semanticSourceId, semanticTargetId);
        long physicalTargetId = Math.max(semanticSourceId, semanticTargetId);
        String type = extracted.getType().trim().toUpperCase(Locale.ROOT);
        LifeGraphRelation relation = relationRepository
                .findByUserIdAndSemanticSourceIdAndSemanticTargetIdAndType(
                        source.userId(), semanticSourceId, semanticTargetId, type)
                .orElseGet(() -> relationRepository
                        .findByUserIdAndSourceIdAndTargetIdAndType(
                                source.userId(), physicalSourceId, physicalTargetId, type)
                        .orElse(null));

        int normalizedCount = Math.max(1, occurrenceCount);
        if (relation == null) {
            relation = LifeGraphRelation.builder()
                    .userId(source.userId())
                    .sourceId(physicalSourceId)
                    .targetId(physicalTargetId)
                    .semanticSourceId(semanticSourceId)
                    .semanticTargetId(semanticTargetId)
                    .type(type)
                    .confidence(toConfidence(extracted.getConfidence()))
                    .weight(normalizedCount)
                    .manualWeight(0)
                    .firstSeen(source.sourceTime() == null ? LocalDateTime.now() : source.sourceTime())
                    .lastSeen(source.sourceTime() == null ? LocalDateTime.now() : source.sourceTime())
                    .evidenceDiaryId(SourceType.DIARY.code().equals(source.sourceType()) ? source.sourceId() : null)
                    .origin(LifeGraphRelation.Origin.AUTO)
                    .props(toJson(extracted.getProps()))
                    .build();
        } else {
            relation.setSemanticSourceId(semanticSourceId);
            relation.setSemanticTargetId(semanticTargetId);
            relation.setWeight((relation.getWeight() == null ? 0 : relation.getWeight()) + normalizedCount);
            relation.setLastSeen(source.sourceTime() == null ? LocalDateTime.now() : source.sourceTime());
            relation.setEvidenceDiaryId(SourceType.DIARY.code().equals(source.sourceType()) ? source.sourceId()
                    : relation.getEvidenceDiaryId());
            BigDecimal confidence = toConfidence(extracted.getConfidence());
            if (relation.getConfidence() == null || relation.getConfidence().compareTo(confidence) < 0) {
                relation.setConfidence(confidence);
            }
        }
        LifeGraphRelation saved = relationRepository.save(relation);
        saveRelationEvidence(source, saved, extracted, normalizedCount);
    }

    private void saveRelationEvidence(SourceContext source, LifeGraphRelation relation,
                                      LifeGraphExtractionResult.ExtractedRelation extracted,
                                      int occurrenceCount) {
        LifeGraphRelationEvidence evidence = relationEvidenceRepository
                .findByUserIdAndRelationIdAndSourceTypeAndSourceId(
                        source.userId(), relation.getId(), source.sourceType(), source.sourceId())
                .orElse(null);
        if (evidence == null) {
            evidence = LifeGraphRelationEvidence.builder()
                    .userId(source.userId())
                    .relationId(relation.getId())
                    .sourceType(source.sourceType())
                    .sourceId(source.sourceId())
                    .build();
        }
        evidence.setOccurrenceCount(Math.max(1, occurrenceCount));
        evidence.setEvidenceSnippet(trimSnippet(extracted.getEvidenceSnippet(), 1000));
        evidence.setConfidence(toConfidence(extracted.getConfidence()));
        relationEvidenceRepository.save(evidence);
    }

    private void saveEntityEvidence(SourceContext source,
                                    Long entityId,
                                    LifeGraphExtractionResult.ExtractedEntity extracted,
                                    LifeGraphExtractionResult.ExtractedMention mention,
                                    String evidenceKind) {
        if (entityEvidenceRepository == null) {
            return;
        }
        LifeGraphEntityEvidence evidence = entityEvidenceRepository
                .findByUserIdAndEntityIdAndSourceTypeAndSourceId(
                        source.userId(), entityId, source.sourceType(), source.sourceId())
                .orElseGet(() -> LifeGraphEntityEvidence.builder()
                        .userId(source.userId())
                        .entityId(entityId)
                        .sourceType(source.sourceType())
                        .sourceId(source.sourceId())
                        .build());
        evidence.setOccurrenceCount(1);
        evidence.setEvidenceKind(evidenceKind);
        evidence.setSnippet(trimSnippet(
                mention == null ? null : mention.getSnippet(), 1000));
        evidence.setEntryDate(source.entryDate());
        evidence.setSourceTime(source.sourceTime());
        evidence.setProps(toJson(mention == null ? null : mention.getProps()));
        entityEvidenceRepository.save(evidence);
    }

    private void ensureDiaryMention(SourceContext source, Long entityId,
                                    LifeGraphExtractionResult.ExtractedMention extracted) {
        List<LifeGraphMention> existing = mentionRepository.findByUserIdAndEntityIdAndDiaryId(
                source.userId(), entityId, source.sourceId());
        LifeGraphMention mention = existing == null || existing.isEmpty()
                ? LifeGraphMention.builder()
                .userId(source.userId())
                .entityId(entityId)
                .diaryId(source.sourceId())
                .build()
                : existing.get(0);
        mention.setEntryDate(source.entryDate());
        mention.setSnippet(trimSnippet(extracted == null ? null : extracted.getSnippet(), 1000));
        mention.setProps(toJson(extracted == null ? null : extracted.getProps()));
        mentionRepository.save(mention);
    }

    private Set<String> findConfirmedImportantPersons(String userId) {
        Set<String> result = new LinkedHashSet<>();
        List<LifeGraphRelation> relations = safeList(relationRepository.findByUserId(userId));
        for (LifeGraphRelation relation : relations) {
            if (!promotionPolicy.personRelations().contains(relation.getType() == null
                    ? "" : relation.getType().trim().toUpperCase(Locale.ROOT))) {
                continue;
            }
            Long sourceId = semanticSourceId(relation);
            Long targetId = semanticTargetId(relation);
            LifeGraphEntity source = entityRepository.findByIdAndUserId(sourceId, userId).orElse(null);
            LifeGraphEntity target = entityRepository.findByIdAndUserId(targetId, userId).orElse(null);
            if (source != null && target != null && source.getType() == LifeGraphEntity.EntityType.User
                    && target.getType() == LifeGraphEntity.EntityType.Person) {
                result.add(target.getNameNorm());
            } else if (source != null && target != null && target.getType() == LifeGraphEntity.EntityType.User
                    && source.getType() == LifeGraphEntity.EntityType.Person) {
                result.add(source.getNameNorm());
            }
        }
        return result;
    }

    private Long findUserEntityId(String userId) {
        return entityRepository.findByUserIdAndTypeAndNameNorm(
                        userId, LifeGraphEntity.EntityType.User, LifeGraphConstants.USER_ENTITY_NORM)
                .map(LifeGraphEntity::getId)
                .orElse(null);
    }

    private void ensureUserEntity(String userId) {
        if (findUserEntityId(userId) != null) {
            return;
        }
        entityRepository.save(LifeGraphEntity.builder()
                .userId(userId)
                .type(LifeGraphEntity.EntityType.User)
                .nameNorm(LifeGraphConstants.USER_ENTITY_NORM)
                .displayName("我")
                .summary("用户自身")
                .mentionCount(0)
                .relationCount(0)
                .importance(1.0)
                .confidence(1.0)
                .matchAllowed(false)
                .hidden(false)
                .origin(LifeGraphEntity.Origin.MANUAL)
                .build());
    }

    private LifeGraphEntity resolveEntityByAliasOrNorm(String userId,
                                                       LifeGraphEntity.EntityType type,
                                                       String nameNorm) {
        LifeGraphEntity byAlias = aliasRepository.findByUserIdAndAliasNorm(userId, nameNorm)
                .flatMap(alias -> entityRepository.findByIdAndUserId(alias.getEntityId(), userId))
                .orElse(null);
        if (byAlias != null) {
            return byAlias;
        }
        return entityRepository.findByUserIdAndTypeAndNameNorm(userId, type, nameNorm).orElse(null);
    }

    private void upsertAlias(String userId, Long entityId, String display, Double confidence) {
        if (entityId == null || StrUtil.isBlank(display)) {
            return;
        }
        String normalized = promotionPolicy.normalizeKey(display, null);
        if (StrUtil.isBlank(normalized) || LifeGraphConstants.USER_ENTITY_NORM.equals(normalized)) {
            return;
        }
        aliasRepository.findByUserIdAndAliasNorm(userId, normalized).ifPresentOrElse(existing -> {
            if (Objects.equals(existing.getEntityId(), entityId)) {
                BigDecimal next = toConfidence(confidence);
                if (existing.getConfidence() == null || existing.getConfidence().compareTo(next) < 0) {
                    existing.setConfidence(next);
                    aliasRepository.save(existing);
                }
            }
        }, () -> aliasRepository.save(LifeGraphEntityAlias.builder()
                .userId(userId)
                .entityId(entityId)
                .aliasNorm(normalized)
                .aliasDisplay(display.trim())
                .confidence(toConfidence(confidence))
                .build()));
    }

    private LifeGraphEntity.EntityType parseType(String value) {
        if (!promotionPolicy.isSupportedType(value)) {
            return null;
        }
        String normalized = value.trim();
        for (LifeGraphEntity.EntityType type : LifeGraphEntity.EntityType.values()) {
            if (type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return null;
    }

    private SourceContext sourceFromDiary(Diary diary, String content) {
        return new SourceContext(SourceType.DIARY.code(), diary.getDiaryId(), diary.getUserId(), content,
                diary.getEntryDate(), diary.getUpdateTime() == null ? LocalDateTime.now() : diary.getUpdateTime(),
                diary.getTitle(), diary.getPlaceName(), diary.getAddress(),
                coordinates(diary.getLatitude(), diary.getLongitude()), diary);
    }

    private SourceContext sourceFromPlaza(CognitionIngestCommand command) {
        LocalDate entryDate = command.getTimestamp() == null ? null : command.getTimestamp().toLocalDate();
        return new SourceContext(SourceType.PLAZA.code(), command.getSourceId(), command.getUserId(),
                command.getMaskedText(), entryDate,
                command.getTimestamp() == null ? LocalDateTime.now() : command.getTimestamp(),
                command.getTitle(), command.getPlaceName(), null, null, null);
    }

    private String evidenceKind(String sourceType, String key,
                                LifeGraphPromotionPolicy.PromotionResult promotion) {
        if (LifeGraphConstants.USER_ENTITY_NORM.equals(key)) {
            return LifeGraphEvidenceKind.USER.code();
        }
        boolean directPerson = promotion.relations().stream().anyMatch(relation -> {
            String type = relation.getType() == null ? "" : relation.getType().toUpperCase(Locale.ROOT);
            String source = promotionPolicy.normalizeKey(relation.getSource(), null);
            String target = promotionPolicy.normalizeKey(relation.getTarget(), null);
            return promotionPolicy.personRelations().contains(type)
                    && (key.equals(source) || key.equals(target));
        });
        return directPerson ? LifeGraphEvidenceKind.USER_RELATION.code() : LifeGraphEvidenceKind.LIFE_ATTRIBUTE.code();
    }

    private void runInTransaction(Runnable action) {
        if (transactionManager == null) {
            action.run();
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private List<LifeGraphEntityEvidence> safeEntityEvidence(List<LifeGraphEntityEvidence> values) {
        return values == null ? List.of() : values;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private Long semanticSourceId(LifeGraphRelation relation) {
        return relation.getSemanticSourceId() == null ? relation.getSourceId() : relation.getSemanticSourceId();
    }

    private Long semanticTargetId(LifeGraphRelation relation) {
        return relation.getSemanticTargetId() == null ? relation.getTargetId() : relation.getSemanticTargetId();
    }

    private LocalDateTime evidenceTime(LifeGraphRelationEvidence evidence) {
        return evidence.getUpdatedAt() == null ? evidence.getCreatedAt() : evidence.getUpdatedAt();
    }

    private int safeOccurrenceCount(Integer count) {
        return count == null || count < 1 ? 1 : count;
    }

    private String buildKnownEntities(String userId) {
        List<LifeGraphEntity> topEntities = entityRepository.findVisibleByUserId(
                userId, LocalDateTime.now(),
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "mentionCount"))).getContent();
        Set<Long> visibleIds = topEntities.stream().map(LifeGraphEntity::getId).collect(Collectors.toSet());
        Map<Long, List<LifeGraphEntityAlias>> aliases = new HashMap<>();
        for (LifeGraphEntityAlias alias : aliasRepository.findTop200ByUserIdOrderByConfidenceDesc(userId)) {
            if (visibleIds.contains(alias.getEntityId())) {
                aliases.computeIfAbsent(alias.getEntityId(), ignored -> new ArrayList<>()).add(alias);
            }
        }
        StringBuilder result = new StringBuilder();
        for (LifeGraphEntity entity : topEntities) {
            if (entity.getType() == LifeGraphEntity.EntityType.User) {
                continue;
            }
            result.append("- ").append(entity.getNameNorm()).append(" (").append(entity.getType()).append(")");
            List<LifeGraphEntityAlias> entityAliases = aliases.get(entity.getId());
            if (entityAliases != null && !entityAliases.isEmpty()) {
                result.append(" aliases:[");
                entityAliases.stream().limit(5).map(LifeGraphEntityAlias::getAliasDisplay)
                        .forEach(alias -> result.append(alias).append(","));
                result.append("]");
            }
            result.append("\n");
        }
        return result.toString();
    }

    private LifeGraphExtractionResult parseExtractionResult(String raw) {
        if (raw == null) {
            return null;
        }
        String json = extractJsonObject(raw);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, LifeGraphExtractionResult.class);
        } catch (Exception exception) {
            log.warn("解析GraphRAG抽取JSON失败: {}", exception.getMessage());
            return null;
        }
    }

    private String mergeProps(String existingJson, Map<String, Object> additions) {
        if (additions == null || additions.isEmpty()) {
            return existingJson;
        }
        Map<String, Object> merged = new HashMap<>();
        try {
            if (StrUtil.isNotBlank(existingJson)) {
                merged.putAll(objectMapper.readValue(existingJson,
                        new TypeReference<Map<String, Object>>() { }));
            }
        } catch (Exception ignored) {
        }
        merged.putAll(additions);
        return toJson(merged);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return null;
        }
    }

    private String extractJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return start >= 0 && end > start ? raw.substring(start, end + 1) : null;
    }

    private String coordinates(Double latitude, Double longitude) {
        return latitude != null && longitude != null ? latitude + "," + longitude : "";
    }

    private double clampImportance(Double value) {
        if (value == null) {
            return 0.5;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private Double toDoubleConfidence(Double value) {
        if (value == null) {
            return 0.5;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private BigDecimal toConfidence(Double value) {
        return BigDecimal.valueOf(value == null ? 0.8 : Math.max(0.0, Math.min(1.0, value)));
    }

    private String trimSnippet(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private record SourceContext(
            String sourceType,
            String sourceId,
            String userId,
            String content,
            LocalDate entryDate,
            LocalDateTime sourceTime,
            String title,
            String placeName,
            String address,
            String coordinates,
            Diary diary) {
    }
}
