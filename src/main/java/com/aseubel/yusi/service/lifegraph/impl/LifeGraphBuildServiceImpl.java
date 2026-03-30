package com.aseubel.yusi.service.lifegraph.impl;

import cn.hutool.core.util.StrUtil;
import jakarta.transaction.Transactional;

import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphEntityAlias;
import com.aseubel.yusi.pojo.entity.LifeGraphMention;
import com.aseubel.yusi.pojo.entity.LifeGraphRelation;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.repository.LifeGraphEntityAliasRepository;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;

import com.aseubel.yusi.repository.LifeGraphMentionRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.aseubel.yusi.service.ai.PromptManager;
import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import com.aseubel.yusi.service.lifegraph.LifeGraphBuildService;
import com.aseubel.yusi.service.lifegraph.ai.LifeGraphExtractor;
import com.aseubel.yusi.service.lifegraph.dto.LifeGraphExtractionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LifeGraphBuildServiceImpl implements LifeGraphBuildService {

    private final LifeGraphEntityRepository entityRepository;
    private final LifeGraphEntityAliasRepository aliasRepository;
    private final LifeGraphRelationRepository relationRepository;
    private final LifeGraphMentionRepository mentionRepository;
    private final PromptManager promptManager;
    private final LifeGraphExtractor extractor;
    private final ObjectMapper objectMapper;

    @Override
    public void upsertFromDiary(Diary diary, String plainContent) {
        if (diary == null || StrUtil.isBlank(plainContent)) {
            return;
        }

        String userId = diary.getUserId();
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
            ModelRouteContextHolder.set(ModelRouteContext.builder().scene("memory-extract").language("zh").build());
            raw = extractor.extract(prompt, knownEntities, entryDate, title, placeName, address, coordinates,
                    plainContent);
        } finally {
            ModelRouteContextHolder.clear();
        }
        LifeGraphExtractionResult result = parseExtractionResult(raw);
        if (result == null) {
            return;
        }

        // 使用单独的方法进行彻底的关联清理与计数核减
        deleteByDiary(userId, diary.getDiaryId());

        Map<String, Long> resolvedEntityIds = new HashMap<>();

        ensureUserEntity(userId);

        if (result.getEntities() != null) {
            for (LifeGraphExtractionResult.ExtractedEntity e : result.getEntities()) {
                Long id = resolveAndUpsertEntity(userId, diary, e);
                if (id != null && StrUtil.isNotBlank(e.getNameNorm())) {
                    resolvedEntityIds.put(normalizeName(e.getNameNorm()), id);
                }
            }
        }

        if (result.getRelations() != null) {
            for (LifeGraphExtractionResult.ExtractedRelation r : result.getRelations()) {
                upsertRelation(userId, diary, r, resolvedEntityIds);
            }
        }

        if (result.getMentions() != null) {
            for (LifeGraphExtractionResult.ExtractedMention m : result.getMentions()) {
                upsertMention(userId, diary, m, resolvedEntityIds);
            }
        }
    }

    @Override
    @Transactional
    public void deleteByDiary(String userId, String diaryId) {
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(diaryId)) {
            return;
        }

        // 1. 获取这篇日记提到的所有 Mention，提取出相关的 EntityId
        List<LifeGraphMention> mentions = mentionRepository.findByUserIdAndDiaryId(userId, diaryId);
        Set<Long> entityIds = mentions.stream()
                .map(LifeGraphMention::getEntityId)
                .collect(java.util.stream.Collectors.toSet());

        // 2. 将这篇日记产生过影响的实体，执行 mentionCount - 1（逆向退回抽卡）
        for (Long entityId : entityIds) {
            entityRepository.findById(entityId).ifPresent(entity -> {
                if (entity.getType() != LifeGraphEntity.EntityType.User && !"__USER__".equals(entity.getNameNorm())) {
                    int newCount = (entity.getMentionCount() == null ? 0 : entity.getMentionCount()) - 1;
                    if (newCount <= 0) {
                        // 如果不再有任何提及，物理删除实体、以及它配套的别名大全，保持图谱干净
                        entityRepository.delete(entity);
                        List<com.aseubel.yusi.pojo.entity.LifeGraphEntityAlias> aliases = aliasRepository
                                .findByUserIdAndEntityId(userId, entity.getId());
                        if (!aliases.isEmpty()) {
                            aliasRepository.deleteAll(aliases);
                        }
                    } else {
                        entity.setMentionCount(newCount);
                        entityRepository.save(entity);
                    }
                }
            });
        }

        // 3. 删除底层的这篇日记直接证据关联记录
        mentionRepository.deleteByUserIdAndDiaryId(userId, diaryId);
        relationRepository.deleteByUserIdAndEvidenceDiaryId(userId, diaryId);
    }

    private void ensureUserEntity(String userId) {
        entityRepository.findByUserIdAndTypeAndNameNorm(userId, LifeGraphEntity.EntityType.User, "__USER__")
                .orElseGet(() -> entityRepository.save(LifeGraphEntity.builder()
                        .userId(userId)
                        .type(LifeGraphEntity.EntityType.User)
                        .nameNorm("__USER__")
                        .displayName("我")
                        .mentionCount(0)
                        .build()));
    }

    private Long resolveAndUpsertEntity(String userId, Diary diary, LifeGraphExtractionResult.ExtractedEntity e) {
        if (e == null) {
            return null;
        }

        String displayName = StrUtil.blankToDefault(e.getDisplayName(), "");
        String nameNorm = StrUtil.blankToDefault(e.getNameNorm(), displayName);
        nameNorm = normalizeName(nameNorm);
        if (StrUtil.isBlank(nameNorm)) {
            return null;
        }

        if ("__USER__".equals(nameNorm) || "我".equals(nameNorm)) {
            ensureUserEntity(userId);
            return entityRepository.findByUserIdAndTypeAndNameNorm(userId, LifeGraphEntity.EntityType.User, "__USER__")
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
                    .firstMentionDate(diary.getEntryDate())
                    .build();
        }

        entity.setMentionCount((entity.getMentionCount() == null ? 0 : entity.getMentionCount()) + 1);
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
                .flatMap(a -> entityRepository.findById(a.getEntityId()))
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
        if (StrUtil.isBlank(aliasNorm) || "__USER__".equals(aliasNorm)) {
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
            Map<String, Long> resolvedEntityIds) {
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

        LifeGraphRelation existing = relationRepository.findByUserIdAndSourceIdAndTargetIdAndType(userId, s, t, type)
                .orElse(null);

        String mergedProps = mergeProps(existing != null ? existing.getProps() : null, r.getProps());
        if (StrUtil.isNotBlank(r.getEvidenceSnippet())) {
            Map<String, Object> evidence = Map.of("evidenceSnippet", trimSnippet(r.getEvidenceSnippet(), 200));
            mergedProps = mergeProps(mergedProps, evidence);
        }

        if (existing == null) {
            relationRepository.save(LifeGraphRelation.builder()
                    .userId(userId)
                    .sourceId(s)
                    .targetId(t)
                    .type(type)
                    .confidence(toConfidence(r.getConfidence()))
                    .weight(1)
                    .firstSeen(LocalDateTime.now())
                    .lastSeen(LocalDateTime.now())
                    .evidenceDiaryId(diary.getDiaryId())
                    .props(mergedProps)
                    .build());
            return;
        }

        existing.setWeight((existing.getWeight() == null ? 0 : existing.getWeight()) + 1);
        existing.setLastSeen(LocalDateTime.now());
        existing.setEvidenceDiaryId(diary.getDiaryId());
        existing.setProps(mergedProps);

        BigDecimal conf = toConfidence(r.getConfidence());
        if (existing.getConfidence() == null || existing.getConfidence().compareTo(conf) < 0) {
            existing.setConfidence(conf);
        }
        relationRepository.save(existing);
    }

    private void upsertMention(String userId, Diary diary, LifeGraphExtractionResult.ExtractedMention m,
            Map<String, Long> resolvedEntityIds) {
        if (m == null || StrUtil.isBlank(m.getEntity())) {
            return;
        }
        Long entityId = resolveEntityId(userId, m.getEntity(), resolvedEntityIds);
        if (entityId == null) {
            return;
        }
        mentionRepository.save(LifeGraphMention.builder()
                .userId(userId)
                .entityId(entityId)
                .diaryId(diary.getDiaryId())
                .entryDate(diary.getEntryDate())
                .snippet(trimSnippet(m.getSnippet(), 1000))
                .props(toJson(m.getProps()))
                .build());
    }

    private Long resolveEntityId(String userId, String key, Map<String, Long> resolvedEntityIds) {
        if (StrUtil.isBlank(key)) {
            return null;
        }
        String norm = normalizeName(key);
        if ("__USER__".equals(norm) || "我".equals(norm)) {
            return entityRepository.findByUserIdAndTypeAndNameNorm(userId, LifeGraphEntity.EntityType.User, "__USER__")
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
        List<LifeGraphEntity> topEntities = entityRepository.findTop50ByUserIdOrderByMentionCountDesc(userId);
        List<LifeGraphEntityAlias> topAliases = aliasRepository.findTop200ByUserIdOrderByConfidenceDesc(userId);
        Map<Long, List<LifeGraphEntityAlias>> aliases = new HashMap<>();
        for (LifeGraphEntityAlias a : topAliases) {
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
