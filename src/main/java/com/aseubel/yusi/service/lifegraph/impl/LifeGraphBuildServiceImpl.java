package com.aseubel.yusi.service.lifegraph.impl;

import cn.hutool.core.util.StrUtil;
import jakarta.transaction.Transactional;

import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphEntityAlias;
import com.aseubel.yusi.pojo.entity.LifeGraphMention;
import com.aseubel.yusi.pojo.entity.LifeGraphRelation;
import com.aseubel.yusi.pojo.entity.LifeGraphRelationEvidence;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.repository.LifeGraphEntityAliasRepository;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;

import com.aseubel.yusi.repository.LifeGraphMentionRepository;
import com.aseubel.yusi.repository.LifeGraphRelationEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import com.aseubel.yusi.service.lifegraph.LifeGraphBuildService;
import com.aseubel.yusi.service.lifegraph.ai.LifeGraphExtractor;
import com.aseubel.yusi.service.lifegraph.dto.LifeGraphExtractionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LifeGraphBuildServiceImpl implements LifeGraphBuildService {

    private static final String USER_ENTITY_NORM = "__user__";

    private final LifeGraphEntityRepository entityRepository;
    private final LifeGraphEntityAliasRepository aliasRepository;
    private final LifeGraphRelationRepository relationRepository;
    private final LifeGraphRelationEvidenceRepository evidenceRepository;
    private final LifeGraphMentionRepository mentionRepository;
    private final PromptManager promptManager;
    private final LifeGraphExtractor extractor;
    private final ObjectMapper objectMapper;

    @Override
    public void upsertFromDiary(Diary diary, String plainContent) {
        if (diary == null) {
            return;
        }

        String userId = diary.getUserId();
        deleteByDiary(userId, diary.getDiaryId());
        if (StrUtil.isBlank(plainContent)) {
            return;
        }

        String prompt = promptManager.getPrompt(PromptKey.GRAPHRAG_EXTRACT);

        String knownEntities = buildKnownEntities(userId);
        String entryDate = diary.getEntryDate() != null ? diary.getEntryDate().toString() : "";
        String title = diary.getTitle() != null ? diary.getTitle() : "";
        String placeName = diary.getPlaceName() != null ? diary.getPlaceName() : "";
        String address = diary.getAddress() != null ? diary.getAddress() : "";
        String coordinates = (diary.getLatitude() != null && diary.getLongitude() != null)
                ? (diary.getLatitude() + "," + diary.getLongitude())
                : "";

        String raw;
        try {
            ModelRouteContextHolder.set(ModelRouteContext.builder().scene(PromptKey.GRAPHRAG_EXTRACT.getKey()).build());
            raw = extractor.extract(prompt, knownEntities, entryDate, title, placeName, address, coordinates,
                    plainContent);
        } finally {
            ModelRouteContextHolder.clear();
        }
        LifeGraphExtractionResult result = parseExtractionResult(raw);
        if (result == null) {
            throw new IllegalStateException("GraphRAG extraction returned invalid JSON");
        }

        Map<String, Long> resolvedEntityIds = new HashMap<>();
        Set<Long> entityContributions = new HashSet<>();
        Set<Long> extractedEntityIds = new LinkedHashSet<>();
        Set<Long> mentionContributions = new HashSet<>();

        ensureUserEntity(userId);

        if (result.getEntities() != null) {
            for (LifeGraphExtractionResult.ExtractedEntity e : result.getEntities()) {
                Long id = resolveAndUpsertEntity(userId, diary, e, entityContributions);
                if (id != null && StrUtil.isNotBlank(e.getNameNorm())) {
                    resolvedEntityIds.put(normalizeName(e.getNameNorm()), id);
                }
                if (id != null) {
                    extractedEntityIds.add(id);
                }
            }
        }

        if (result.getRelations() != null) {
            Map<String, LifeGraphExtractionResult.ExtractedRelation> relationByKey = new LinkedHashMap<>();
            Map<String, Integer> relationOccurrences = new HashMap<>();
            for (LifeGraphExtractionResult.ExtractedRelation r : result.getRelations()) {
                String key = relationKey(userId, r, resolvedEntityIds);
                if (key == null) {
                    continue;
                }
                relationByKey.putIfAbsent(key, r);
                relationOccurrences.merge(key, 1, Integer::sum);
            }
            for (Map.Entry<String, LifeGraphExtractionResult.ExtractedRelation> entry : relationByKey.entrySet()) {
                upsertRelation(userId, diary, entry.getValue(), resolvedEntityIds,
                        relationOccurrences.getOrDefault(entry.getKey(), 1));
            }
        }

        if (result.getMentions() != null) {
            for (LifeGraphExtractionResult.ExtractedMention m : result.getMentions()) {
                upsertMention(userId, diary, m, resolvedEntityIds, mentionContributions);
            }
        }

        for (Long entityId : extractedEntityIds) {
            ensureDiaryMention(userId, diary, entityId, mentionContributions);
        }
    }

    @Override
    @Transactional
    public void deleteByDiary(String userId, String diaryId) {
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(diaryId)) {
            return;
        }

        List<LifeGraphMention> mentions = mentionRepository.findByUserIdAndDiaryId(userId, diaryId);
        Set<Long> entityIds = mentions.stream()
                .map(LifeGraphMention::getEntityId)
                .collect(java.util.stream.Collectors.toSet());

        mentionRepository.deleteByUserIdAndDiaryId(userId, diaryId);

        for (Long entityId : entityIds) {
            entityRepository.findByIdAndUserId(entityId, userId).ifPresent(entity -> {
                List<LifeGraphMention> allMentions = Optional
                        .ofNullable(mentionRepository.findByUserIdAndEntityId(userId, entityId))
                        .orElseGet(List::of);
                int remainingCount = (int) allMentions.stream()
                        .map(LifeGraphMention::getDiaryId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .count();
                if (entity.getOrigin() == LifeGraphEntity.Origin.AUTO) {
                    if (entity.getType() != LifeGraphEntity.EntityType.User
                            && !USER_ENTITY_NORM.equalsIgnoreCase(entity.getNameNorm())
                            && remainingCount == 0) {
                        entityRepository.delete(entity);
                        List<LifeGraphEntityAlias> aliases = aliasRepository.findByUserIdAndEntityId(userId, entity.getId());
                        if (!aliases.isEmpty()) {
                            aliasRepository.deleteAll(aliases);
                        }
                        return;
                    }
                    entity.setMentionCount(remainingCount);
                    entityRepository.save(entity);
                }
            });
        }

        List<LifeGraphRelationEvidence> evidences = Optional
                .ofNullable(evidenceRepository.findByUserIdAndSourceTypeAndSourceId(userId, "DIARY", diaryId))
                .orElseGet(List::of);
        Set<Long> relationIds = evidences.stream()
                .map(LifeGraphRelationEvidence::getRelationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (Long relationId : relationIds) {
            Optional<LifeGraphRelation> relationOptional = relationRepository.findByIdAndUserId(relationId, userId);
            if (relationOptional.isEmpty()) {
                continue;
            }

            LifeGraphRelation relation = relationOptional.get();
            List<LifeGraphRelationEvidence> allEvidence = Optional
                    .ofNullable(evidenceRepository.findByUserIdAndRelationId(userId, relationId))
                    .orElseGet(List::of);
            List<LifeGraphRelationEvidence> remainingEvidence = allEvidence.stream()
                    .filter(evidence -> !Objects.equals(evidence.getSourceType(), "DIARY")
                            || !Objects.equals(evidence.getSourceId(), diaryId))
                    .toList();
            int remainingWeight = remainingEvidence.stream()
                    .mapToInt(evidence -> safeOccurrenceCount(evidence.getOccurrenceCount()))
                    .sum();
            int remainingDiaryWeight = remainingEvidence.stream()
                    .filter(evidence -> "DIARY".equals(evidence.getSourceType()))
                    .mapToInt(evidence -> safeOccurrenceCount(evidence.getOccurrenceCount()))
                    .sum();

            if (relation.getOrigin() == LifeGraphRelation.Origin.AUTO && remainingEvidence.isEmpty()) {
                evidenceRepository.deleteByUserIdAndRelationId(userId, relationId);
                relationRepository.delete(relation);
                continue;
            }

            if (relation.getOrigin() == LifeGraphRelation.Origin.AUTO) {
                relation.setWeight(remainingWeight);
            } else {
                int manualWeight = relation.getManualWeight() == null
                        ? Math.max(0, (relation.getWeight() == null ? 0 : relation.getWeight())
                                - allEvidence.stream()
                                        .filter(evidence -> "DIARY".equals(evidence.getSourceType()))
                                        .mapToInt(evidence -> safeOccurrenceCount(evidence.getOccurrenceCount()))
                                        .sum())
                        : Math.max(0, relation.getManualWeight());
                relation.setManualWeight(manualWeight);
                relation.setWeight(manualWeight + remainingDiaryWeight);
            }
            relation.setEvidenceDiaryId(remainingEvidence.stream()
                    .filter(evidence -> "DIARY".equals(evidence.getSourceType()))
                    .max((left, right) -> compareEvidenceTime(left, right))
                    .map(LifeGraphRelationEvidence::getSourceId)
                    .orElse(null));
            relationRepository.save(relation);
        }

        evidenceRepository.deleteByUserIdAndSourceTypeAndSourceId(userId, "DIARY", diaryId);
    }

    private void ensureUserEntity(String userId) {
        entityRepository.findByUserIdAndTypeAndNameNorm(userId, LifeGraphEntity.EntityType.User, USER_ENTITY_NORM)
                .orElseGet(() -> entityRepository.save(LifeGraphEntity.builder()
                        .userId(userId)
                        .type(LifeGraphEntity.EntityType.User)
                        .nameNorm(USER_ENTITY_NORM)
                        .displayName("我")
                        .mentionCount(0)
                        .build()));
    }

    private Long resolveAndUpsertEntity(String userId, Diary diary, LifeGraphExtractionResult.ExtractedEntity e,
            Set<Long> entityContributions) {
        if (e == null) {
            return null;
        }

        String displayName = StrUtil.blankToDefault(e.getDisplayName(), "");
        String nameNorm = StrUtil.blankToDefault(e.getNameNorm(), displayName);
        nameNorm = normalizeName(nameNorm);
        if (StrUtil.isBlank(nameNorm)) {
            return null;
        }

        if (USER_ENTITY_NORM.equalsIgnoreCase(nameNorm) || "我".equals(nameNorm)) {
            ensureUserEntity(userId);
            return entityRepository.findByUserIdAndTypeAndNameNorm(userId, LifeGraphEntity.EntityType.User,
                    USER_ENTITY_NORM)
                    .map(LifeGraphEntity::getId).orElse(null);
        }

        LifeGraphEntity.EntityType type = parseType(e.getType());

        LifeGraphEntity entity = resolveEntityByAliasOrNorm(userId, type, nameNorm);
        if (entity == null) {
            entity = LifeGraphEntity.builder()
                    .userId(userId)
                    .type(type)
                    .nameNorm(nameNorm)
                    .displayName(StrUtil.isBlank(displayName) ? nameNorm : displayName)
                    .mentionCount(0)
                    .confidence(0.5)
                    .matchAllowed(false)
                    .hidden(false)
                    .origin(LifeGraphEntity.Origin.AUTO)
                    .firstMentionDate(diary.getEntryDate())
                    .build();
        }

        boolean newContribution = entity.getId() == null || entityContributions.add(entity.getId());
        if (entity.getOrigin() == LifeGraphEntity.Origin.AUTO && newContribution) {
            entity.setMentionCount((entity.getMentionCount() == null ? 0 : entity.getMentionCount()) + 1);
        }
        entity.setLastMentionAt(LocalDateTime.now());
        if (entity.getFirstMentionDate() == null) {
            entity.setFirstMentionDate(diary.getEntryDate());
        }

        // AI 生成的摘要（仅在首次创建或原摘要为空时更新）
        if (StrUtil.isNotBlank(e.getSummary()) && StrUtil.isBlank(entity.getSummary())) {
            entity.setSummary(e.getSummary());
        }

        String mergedProps = mergeProps(entity.getProps(), e.getProps());

        // 存储 AI 分析的 emotion 和 importance 到 props
        if (StrUtil.isNotBlank(e.getEmotion())) {
            Map<String, Object> emotionProp = new HashMap<>();
            emotionProp.put("emotion", e.getEmotion());
            mergedProps = mergeProps(mergedProps, emotionProp);
        }
        if (e.getImportance() != null && e.getImportance() > 0) {
            Map<String, Object> importanceProp = new HashMap<>();
            importanceProp.put("importance", e.getImportance());
            mergedProps = mergeProps(mergedProps, importanceProp);
        }

        if (type == LifeGraphEntity.EntityType.Place && diary.getLatitude() != null && diary.getLongitude() != null) {
            Map<String, Object> geo = new HashMap<>();
            Map<String, Object> coordinates = new HashMap<>();
            coordinates.put("lat", diary.getLatitude());
            coordinates.put("lng", diary.getLongitude());
            geo.put("coordinates", coordinates);
            if (StrUtil.isNotBlank(diary.getAddress())) {
                geo.put("address", diary.getAddress());
            }
            if (StrUtil.isNotBlank(diary.getPlaceId())) {
                geo.put("placeId", diary.getPlaceId());
            }
            mergedProps = mergeProps(mergedProps, geo);
        }
        entity.setProps(mergedProps);

        LifeGraphEntity saved = entityRepository.save(entity);
        if (saved.getId() != null && saved.getOrigin() == LifeGraphEntity.Origin.AUTO) {
            entityContributions.add(saved.getId());
        }

        List<String> aliases = e.getAliases() != null ? e.getAliases() : List.of();
        upsertAlias(userId, saved.getId(), saved.getDisplayName(), e.getConfidence());
        for (String a : aliases) {
            upsertAlias(userId, saved.getId(), a, e.getConfidence());
        }

        return saved.getId();
    }

    private LifeGraphEntity resolveEntityByAliasOrNorm(String userId, LifeGraphEntity.EntityType type,
            String nameNorm) {
        LifeGraphEntity byAlias = aliasRepository.findByUserIdAndAliasNorm(userId, nameNorm)
                .flatMap(a -> entityRepository.findByIdAndUserId(a.getEntityId(), userId))
                .orElse(null);
        if (byAlias != null) {
            return byAlias;
        }

        return entityRepository.findByUserIdAndTypeAndNameNorm(userId, type, nameNorm).orElse(null);
    }

    private void upsertAlias(String userId, Long entityId, String aliasDisplay, Double confidence) {
        if (StrUtil.isBlank(aliasDisplay)) {
            return;
        }
        String aliasNorm = normalizeName(aliasDisplay);
        if (StrUtil.isBlank(aliasNorm) || USER_ENTITY_NORM.equalsIgnoreCase(aliasNorm)) {
            return;
        }

        aliasRepository.findByUserIdAndAliasNorm(userId, aliasNorm).ifPresentOrElse(existing -> {
            if (!Objects.equals(existing.getEntityId(), entityId)) {
                return;
            }
            BigDecimal conf = toConfidence(confidence);
            if (existing.getConfidence() == null || existing.getConfidence().compareTo(conf) < 0) {
                existing.setConfidence(conf);
                aliasRepository.save(existing);
            }
        }, () -> {
            aliasRepository.save(LifeGraphEntityAlias.builder()
                    .userId(userId)
                    .entityId(entityId)
                    .aliasNorm(aliasNorm)
                    .aliasDisplay(aliasDisplay)
                    .confidence(toConfidence(confidence))
                    .build());
        });
    }

    private void upsertRelation(String userId, Diary diary, LifeGraphExtractionResult.ExtractedRelation r,
            Map<String, Long> resolvedEntityIds, int occurrenceCount) {
        if (r == null || StrUtil.isBlank(r.getType())) {
            return;
        }

        Long sourceId = resolveEntityId(userId, r.getSource(), resolvedEntityIds);
        Long targetId = resolveEntityId(userId, r.getTarget(), resolvedEntityIds);
        if (sourceId == null || targetId == null || Objects.equals(sourceId, targetId)) {
            return;
        }

        long s = Math.min(sourceId, targetId);
        long t = Math.max(sourceId, targetId);
        String type = r.getType().trim();
        int normalizedOccurrenceCount = Math.max(1, occurrenceCount);

        LifeGraphRelation existing = relationRepository.findByUserIdAndSourceIdAndTargetIdAndType(userId, s, t, type)
                .orElse(null);

        String mergedProps = mergeProps(existing != null ? existing.getProps() : null, r.getProps());
        if (StrUtil.isNotBlank(r.getEvidenceSnippet())) {
            Map<String, Object> evidence = Map.of("evidenceSnippet", trimSnippet(r.getEvidenceSnippet(), 200));
            mergedProps = mergeProps(mergedProps, evidence);
        }

        if (existing == null) {
            LifeGraphRelation saved = relationRepository.save(LifeGraphRelation.builder()
                    .userId(userId)
                    .sourceId(s)
                    .targetId(t)
                    .type(type)
                    .confidence(toConfidence(r.getConfidence()))
                    .weight(normalizedOccurrenceCount)
                    .firstSeen(LocalDateTime.now())
                    .lastSeen(LocalDateTime.now())
                    .evidenceDiaryId(diary.getDiaryId())
                    .origin(LifeGraphRelation.Origin.AUTO)
                    .props(mergedProps)
                    .build());
            saveRelationEvidence(userId, saved, diary, r, normalizedOccurrenceCount);
            return;
        }

        existing.setWeight((existing.getWeight() == null ? 0 : existing.getWeight()) + normalizedOccurrenceCount);
        existing.setLastSeen(LocalDateTime.now());
        existing.setEvidenceDiaryId(diary.getDiaryId());
        existing.setProps(mergedProps);

        BigDecimal conf = toConfidence(r.getConfidence());
        if (existing.getConfidence() == null || existing.getConfidence().compareTo(conf) < 0) {
            existing.setConfidence(conf);
        }
        LifeGraphRelation saved = relationRepository.save(existing);
        saveRelationEvidence(userId, saved, diary, r, normalizedOccurrenceCount);
    }

    private String relationKey(String userId, LifeGraphExtractionResult.ExtractedRelation relation,
            Map<String, Long> resolvedEntityIds) {
        if (relation == null || StrUtil.isBlank(relation.getType())) {
            return null;
        }
        Long sourceId = resolveEntityId(userId, relation.getSource(), resolvedEntityIds);
        Long targetId = resolveEntityId(userId, relation.getTarget(), resolvedEntityIds);
        if (sourceId == null || targetId == null || Objects.equals(sourceId, targetId)) {
            return null;
        }
        return Math.min(sourceId, targetId) + "|" + Math.max(sourceId, targetId) + "|" + relation.getType().trim();
    }

    private void upsertMention(String userId, Diary diary, LifeGraphExtractionResult.ExtractedMention m,
            Map<String, Long> resolvedEntityIds, Set<Long> mentionContributions) {
        if (m == null || StrUtil.isBlank(m.getEntity())) {
            return;
        }
        Long entityId = resolveEntityId(userId, m.getEntity(), resolvedEntityIds);
        if (entityId == null) {
            return;
        }
        ensureDiaryMention(userId, diary, entityId, mentionContributions, m);
    }

    private void ensureDiaryMention(String userId, Diary diary, Long entityId, Set<Long> mentionContributions) {
        ensureDiaryMention(userId, diary, entityId, mentionContributions, null);
    }

    private void ensureDiaryMention(String userId, Diary diary, Long entityId, Set<Long> mentionContributions,
            LifeGraphExtractionResult.ExtractedMention extracted) {
        if (entityId == null || !mentionContributions.add(entityId)) {
            return;
        }
        mentionRepository.save(LifeGraphMention.builder()
                .userId(userId)
                .entityId(entityId)
                .diaryId(diary.getDiaryId())
                .entryDate(diary.getEntryDate())
                .snippet(extracted == null ? null : trimSnippet(extracted.getSnippet(), 1000))
                .props(extracted == null ? null : toJson(extracted.getProps()))
                .build());
    }

    private void saveRelationEvidence(String userId, LifeGraphRelation relation, Diary diary,
            LifeGraphExtractionResult.ExtractedRelation extracted, int occurrenceCount) {
        if (relation == null || relation.getId() == null) {
            return;
        }
        LifeGraphRelationEvidence evidence = evidenceRepository
                .findByUserIdAndRelationIdAndSourceTypeAndSourceId(
                        userId, relation.getId(), "DIARY", diary.getDiaryId())
                .orElse(null);
        if (evidence == null) {
            evidence = LifeGraphRelationEvidence.builder()
                    .userId(userId)
                    .relationId(relation.getId())
                    .sourceType("DIARY")
                    .sourceId(diary.getDiaryId())
                    .occurrenceCount(occurrenceCount)
                    .build();
        } else {
            evidence.setOccurrenceCount((evidence.getOccurrenceCount() == null
                    ? 0
                    : Math.max(0, evidence.getOccurrenceCount())) + occurrenceCount);
        }
        evidence.setEvidenceSnippet(trimSnippet(extracted.getEvidenceSnippet(), 1000));
        evidence.setConfidence(toConfidence(extracted.getConfidence()));
        evidenceRepository.save(evidence);
    }

    private int safeOccurrenceCount(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }

    private int compareEvidenceTime(LifeGraphRelationEvidence left, LifeGraphRelationEvidence right) {
        LocalDateTime leftTime = left.getUpdatedAt() != null ? left.getUpdatedAt() : left.getCreatedAt();
        LocalDateTime rightTime = right.getUpdatedAt() != null ? right.getUpdatedAt() : right.getCreatedAt();
        if (leftTime == null) return rightTime == null ? 0 : -1;
        if (rightTime == null) return 1;
        return leftTime.compareTo(rightTime);
    }

    private Long resolveEntityId(String userId, String key, Map<String, Long> resolvedEntityIds) {
        if (StrUtil.isBlank(key)) {
            return null;
        }
        String norm = normalizeName(key);
        if (USER_ENTITY_NORM.equalsIgnoreCase(norm) || "我".equals(norm)) {
            return entityRepository.findByUserIdAndTypeAndNameNorm(userId, LifeGraphEntity.EntityType.User,
                    USER_ENTITY_NORM)
                    .map(LifeGraphEntity::getId).orElse(null);
        }

        Long cached = resolvedEntityIds.get(norm);
        if (cached != null) {
            return cached;
        }

        LifeGraphEntity byAlias = aliasRepository.findByUserIdAndAliasNorm(userId, norm)
                .flatMap(a -> entityRepository.findById(a.getEntityId()))
                .orElse(null);
        if (byAlias != null) {
            return byAlias.getId();
        }

        List<LifeGraphEntity> candidates = entityRepository.findByUserIdAndNameNorm(userId, norm);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .filter(e -> e.getType() != LifeGraphEntity.EntityType.User)
                .max(Comparator.comparingInt(e -> e.getMentionCount() == null ? 0 : e.getMentionCount()))
                .map(LifeGraphEntity::getId)
                .orElse(null);
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
        } catch (Exception e) {
            log.warn("解析GraphRAG抽取JSON失败: {}", e.getMessage());
            return null;
        }
    }

    private String buildKnownEntities(String userId) {
        List<LifeGraphEntity> topEntities = entityRepository.findVisibleByUserId(
                userId,
                LocalDateTime.now(),
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "mentionCount")))
                .getContent();
        Set<Long> visibleEntityIds = topEntities.stream()
                .map(LifeGraphEntity::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<LifeGraphEntityAlias> topAliases = aliasRepository.findTop200ByUserIdOrderByConfidenceDesc(userId);
        Map<Long, List<LifeGraphEntityAlias>> aliases = new HashMap<>();
        for (LifeGraphEntityAlias a : topAliases) {
            if (!visibleEntityIds.contains(a.getEntityId())) {
                continue;
            }
            aliases.computeIfAbsent(a.getEntityId(), k -> new ArrayList<>()).add(a);
        }

        StringBuilder sb = new StringBuilder();
        for (LifeGraphEntity e : topEntities) {
            if (e.getType() == LifeGraphEntity.EntityType.User) {
                continue;
            }
            sb.append("- ").append(e.getNameNorm()).append(" (").append(e.getType()).append(")");
            List<LifeGraphEntityAlias> as = aliases.get(e.getId());
            if (as != null && !as.isEmpty()) {
                sb.append(" aliases:[");
                int count = 0;
                for (LifeGraphEntityAlias a : as) {
                    if (count++ >= 5)
                        break;
                    sb.append(a.getAliasDisplay());
                    if (count < Math.min(5, as.size())) {
                        sb.append(", ");
                    }
                }
                sb.append("]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String mergeProps(String existingJson, Map<String, Object> toMerge) {
        if (toMerge == null || toMerge.isEmpty()) {
            return existingJson;
        }
        Map<String, Object> merged = new HashMap<>();
        try {
            if (StrUtil.isNotBlank(existingJson)) {
                merged.putAll(objectMapper.readValue(existingJson, new TypeReference<Map<String, Object>>() {
                }));
            }
        } catch (Exception ignored) {
        }
        merged.putAll(toMerge);
        return toJson(merged);
    }

    private String mergeProps(String existingJson, String toMergeJson) {
        if (StrUtil.isBlank(toMergeJson)) {
            return existingJson;
        }
        try {
            Map<String, Object> m = objectMapper.readValue(toMergeJson, new TypeReference<Map<String, Object>>() {
            });
            return mergeProps(existingJson, m);
        } catch (Exception e) {
            return existingJson;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private LifeGraphEntity.EntityType parseType(String value) {
        if (StrUtil.isBlank(value)) {
            return LifeGraphEntity.EntityType.Topic;
        }
        try {
            return LifeGraphEntity.EntityType.valueOf(value.trim());
        } catch (Exception e) {
            return LifeGraphEntity.EntityType.Topic;
        }
    }

    private BigDecimal toConfidence(Double value) {
        if (value == null) {
            return BigDecimal.valueOf(0.800);
        }
        double v = Math.max(0.0, Math.min(1.0, value));
        return BigDecimal.valueOf(v);
    }

    private String normalizeName(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return null;
        }
        v = v.replaceAll("\\s+", "");
        return v.toLowerCase();
    }

    private String extractJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return raw.substring(start, end + 1);
    }

    private String trimSnippet(String v, int max) {
        if (v == null) {
            return null;
        }
        String s = v.trim();
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

}
