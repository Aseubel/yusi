package com.aseubel.yusi.service.lifegraph;

import cn.hutool.core.util.StrUtil;
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

    public static final String USER_KEY = "__user__";
    public static final double MIN_RELATION_CONFIDENCE = 0.6;

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "PERSON", "EVENT", "PLACE", "EMOTION", "TOPIC", "ITEM", "WORK", "USER");

    private static final Set<String> PERSON_RELATIONS = Set.of(
            "PARTNER_OF", "FAMILY_OF", "FRIEND_OF", "COLLEAGUE_OF",
            "MENTOR_OF", "SIBLING_OF", "PARENT_OF", "CHILD_OF");

    private static final Set<String> VALUE_RELATIONS = Set.of(
            "LIKES", "DISLIKES", "BOUGHT_FOR", "PARTICIPATED_IN", "EXPERIENCED",
            "HAPPENED_AT", "TRIGGERED", "WORKED_AT", "LIVED_AT", "CARED_FOR",
            "HAS_BIRTHDAY", "HAS_IMPORTANT_EVENT", "VISITED", "ATTENDED");

    private static final Set<String> USER_TO_PERSON_ACTIONS = Set.of("BOUGHT_FOR", "CARED_FOR");

    private static final Set<String> REJECTED_RELATIONS = Set.of(
            "MENTIONED", "MENTIONED_IN", "SAID", "RELATED_TO");

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
            String source = normalizeKey(relation.getSource(), null);
            String target = normalizeKey(relation.getTarget(), null);
            if (!PERSON_RELATIONS.contains(type)
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
            String source = normalizeKey(relation.getSource(), null);
            String target = normalizeKey(relation.getTarget(), null);
            if (!VALUE_RELATIONS.contains(type)
                    || REJECTED_RELATIONS.contains(type)
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

            boolean otherIsPerson = other != null && "PERSON".equalsIgnoreCase(other.getType());
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
        return type != null && SUPPORTED_TYPES.contains(type.trim().toUpperCase(Locale.ROOT));
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
            return USER_KEY;
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
        return isSupportedEntity(entity) && "PERSON".equalsIgnoreCase(entity.getType());
    }

    private boolean isUserKey(String key) {
        return USER_KEY.equals(normalizeKey(key, null));
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
