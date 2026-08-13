package com.aseubel.yusi.service.lifegraph;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.service.lifegraph.constant.LifeGraphConstants;
import com.aseubel.yusi.service.lifegraph.constant.LifeGraphRelationType;
import com.aseubel.yusi.service.lifegraph.dto.LifeGraphExtractionResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Server-side gate between wide local extraction and long-term LifeGraph data.
 *
 * <p>The model may identify more context than the long-term graph keeps. This
 * policy is deliberately pure so its promotion boundary can be tested without
 * a database or an LLM.</p>
 */
@Component
public class LifeGraphPromotionPolicy {

    public static final double MIN_RELATION_CONFIDENCE = 0.6;

    private static final Set<String> SUPPORTED_TYPES = supportedEntityTypes();
    private static final Set<String> PERSON_RELATIONS = relationCodes(RelationGroup.PERSON);
    private static final Set<String> VALUE_RELATIONS = relationCodes(RelationGroup.VALUE);
    private static final Set<String> USER_TO_PERSON_ACTIONS = Set.of(
            LifeGraphRelationType.BOUGHT_FOR.code(), LifeGraphRelationType.CARED_FOR.code());
    private static final Set<String> REJECTED_RELATIONS = relationCodes(RelationGroup.REJECTED);

    public PromotionResult promote(LifeGraphExtractionResult extraction,
                                   Set<String> confirmedImportantPersonKeys) {
        if (extraction == null) {
            return PromotionResult.empty();
        }

        Map<String, LifeGraphExtractionResult.ExtractedEntity> entities = new HashMap<>();
        if (extraction.getEntities() != null) {
            for (LifeGraphExtractionResult.ExtractedEntity entity : extraction.getEntities()) {
                if (entity == null || !isSupportedType(entity.getType())) {
                    continue;
                }
                String key = normalizeKey(entity.getNameNorm(), entity.getDisplayName());
                if (StrUtil.isNotBlank(key)) {
                    entities.putIfAbsent(key, entity);
                }
            }
        }

        Set<String> importantPersons = new LinkedHashSet<>();
        if (confirmedImportantPersonKeys != null) {
            for (String key : confirmedImportantPersonKeys) {
                String normalized = normalizeKey(key, null);
                if (isPerson(entities.get(normalized))) {
                    importantPersons.add(normalized);
                }
            }
        }

        List<LifeGraphExtractionResult.ExtractedRelation> acceptedRelations = new ArrayList<>();
        Set<String> acceptedRelationKeys = new HashSet<>();
        Map<String, Integer> relationOccurrences = new HashMap<>();

        // Direct User -> Person facts are the only automatic entry point for
        // a new important person.
        for (LifeGraphExtractionResult.ExtractedRelation relation : safeRelations(extraction)) {
            String type = normalizeRelationType(relation.getType());
            LifeGraphRelationType relationType = LifeGraphRelationType.fromCode(type);
            String source = normalizeKey(relation.getSource(), null);
            String target = normalizeKey(relation.getTarget(), null);
            if (relationType == null || !relationType.isPersonRelation()
                    || !hasEvidence(relation)
                    || !hasSufficientConfidence(relation)
                    || source == null
                    || target == null) {
                continue;
            }

            String personKey = null;
            if (isUserKey(source) && isPerson(entities.get(target))) {
                personKey = target;
            } else if (isUserKey(target) && isPerson(entities.get(source))) {
                personKey = source;
            }
            if (personKey != null) {
                importantPersons.add(personKey);
                addAcceptedRelation(acceptedRelations, acceptedRelationKeys, relation, source, target, type);
                relationOccurrences.merge(relationKey(source, target, type), 1, Integer::sum);
            }
        }

        // A confirmed Person may contribute attributes or events, but cannot
        // automatically introduce another Person node.
        for (LifeGraphExtractionResult.ExtractedRelation relation : safeRelations(extraction)) {
            String type = normalizeRelationType(relation.getType());
            LifeGraphRelationType relationType = LifeGraphRelationType.fromCode(type);
            String source = normalizeKey(relation.getSource(), null);
            String target = normalizeKey(relation.getTarget(), null);
            if (relationType == null || !relationType.isValueRelation()
                    || relationType.isRejectedForAutomaticGraph()
                    || !hasEvidence(relation)
                    || !hasSufficientConfidence(relation)
                    || source == null
                    || target == null
                    ) {
                continue;
            }

            boolean sourceIsUser = isUserKey(source);
            boolean targetIsUser = isUserKey(target);
            boolean sourceIsPerson = importantPersons.contains(source);
            boolean targetIsPerson = importantPersons.contains(target);
            String otherKey = sourceIsPerson || sourceIsUser ? target : source;
            LifeGraphExtractionResult.ExtractedEntity other = entities.get(otherKey);

            boolean otherIsPerson = isPerson(other);
            boolean allowedUserPersonAction = USER_TO_PERSON_ACTIONS.contains(type)
                    && sourceIsUser
                    && importantPersons.contains(target)
                    && otherIsPerson;
            if ((sourceIsUser || targetIsUser || sourceIsPerson || targetIsPerson)
                    && other != null
                    && other.getType() != null
                    && (!otherIsPerson || allowedUserPersonAction)
                    && !isUserKey(otherKey)) {
                addAcceptedRelation(acceptedRelations, acceptedRelationKeys, relation, source, target, type);
                relationOccurrences.merge(relationKey(source, target, type), 1, Integer::sum);
            }
        }

        Set<String> acceptedEntityKeys = new LinkedHashSet<>();
        for (LifeGraphExtractionResult.ExtractedRelation relation : acceptedRelations) {
            String source = normalizeKey(relation.getSource(), null);
            String target = normalizeKey(relation.getTarget(), null);
            if (!isUserKey(source) && entities.containsKey(source)) {
                acceptedEntityKeys.add(source);
            }
            if (!isUserKey(target) && entities.containsKey(target)) {
                acceptedEntityKeys.add(target);
            }
        }

        List<LifeGraphExtractionResult.ExtractedEntity> acceptedEntities = acceptedEntityKeys.stream()
                .map(entities::get)
                .filter(entity -> entity != null)
                .toList();
        return new PromotionResult(acceptedEntities, acceptedRelations, acceptedEntityKeys, relationOccurrences);
    }

    public boolean isSupportedType(String type) {
        if (type == null) {
            return false;
        }
        for (LifeGraphEntity.EntityType entityType : LifeGraphEntity.EntityType.values()) {
            if (entityType.name().equalsIgnoreCase(type.trim())) {
                return true;
            }
        }
        return false;
    }

    public Set<String> supportedTypes() {
        return Collections.unmodifiableSet(SUPPORTED_TYPES);
    }

    public Set<String> personRelations() {
        return Collections.unmodifiableSet(PERSON_RELATIONS);
    }

    public Set<String> valueRelations() {
        return Collections.unmodifiableSet(VALUE_RELATIONS);
    }

    public boolean isRejectedRelation(String type) {
        return REJECTED_RELATIONS.contains(normalizeRelationType(type));
    }

    public String normalizeKey(String value, String fallback) {
        String candidate = StrUtil.blankToDefault(value, fallback);
        if (StrUtil.isBlank(candidate)) {
            return null;
        }
        String normalized = candidate.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if ("我".equals(normalized) || "用户".equals(normalized) || "__user__".equals(normalized)) {
            return LifeGraphConstants.USER_ENTITY_NORM;
        }
        return normalized;
    }

    private List<LifeGraphExtractionResult.ExtractedRelation> safeRelations(
            LifeGraphExtractionResult extraction) {
        return extraction.getRelations() == null ? List.of() : extraction.getRelations();
    }

    private boolean isSupportedEntity(LifeGraphExtractionResult.ExtractedEntity entity) {
        return entity != null && isSupportedType(entity.getType());
    }

    private boolean isPerson(LifeGraphExtractionResult.ExtractedEntity entity) {
        return isSupportedEntity(entity)
                && LifeGraphEntity.EntityType.Person.name().equalsIgnoreCase(entity.getType());
    }

    private boolean isUserKey(String key) {
        return LifeGraphConstants.USER_ENTITY_NORM.equals(normalizeKey(key, null));
    }

    private boolean hasEvidence(LifeGraphExtractionResult.ExtractedRelation relation) {
        return StrUtil.isNotBlank(relation.getEvidenceSnippet());
    }

    private boolean hasSufficientConfidence(LifeGraphExtractionResult.ExtractedRelation relation) {
        return relation.getConfidence() != null
                && relation.getConfidence() >= MIN_RELATION_CONFIDENCE;
    }

    private void addAcceptedRelation(List<LifeGraphExtractionResult.ExtractedRelation> acceptedRelations,
                                     Set<String> acceptedRelationKeys,
                                     LifeGraphExtractionResult.ExtractedRelation relation,
                                     String source, String target, String type) {
        String key = relationKey(source, target, type);
        if (acceptedRelationKeys.add(key)) {
            acceptedRelations.add(relation);
        }
    }

    private String relationKey(String source, String target, String type) {
        return source + "|" + target + "|" + type;
    }

    private String normalizeRelationType(String type) {
        return type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
    }

    private static Set<String> supportedEntityTypes() {
        Set<String> types = new LinkedHashSet<>();
        for (LifeGraphEntity.EntityType type : LifeGraphEntity.EntityType.values()) {
            types.add(type.name().toUpperCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(types);
    }

    private static Set<String> relationCodes(RelationGroup group) {
        Set<String> codes = new LinkedHashSet<>();
        for (LifeGraphRelationType relationType : LifeGraphRelationType.values()) {
            if ((group == RelationGroup.PERSON && relationType.isPersonRelation())
                    || (group == RelationGroup.VALUE && relationType.isValueRelation())
                    || (group == RelationGroup.REJECTED && relationType.isRejectedForAutomaticGraph())) {
                codes.add(relationType.code());
            }
        }
        return Collections.unmodifiableSet(codes);
    }

    private enum RelationGroup {
        PERSON,
        VALUE,
        REJECTED
    }

    public record PromotionResult(
            List<LifeGraphExtractionResult.ExtractedEntity> entities,
            List<LifeGraphExtractionResult.ExtractedRelation> relations,
            Set<String> acceptedEntityKeys,
            Map<String, Integer> relationOccurrences) {

        public static PromotionResult empty() {
            return new PromotionResult(List.of(), List.of(), Set.of(), Map.of());
        }
    }
}
