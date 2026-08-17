package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.service.lifegraph.dto.LifeGraphExtractionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifeGraphPromotionPolicyTest {

    @Test
    void enforcesInclusiveConfidenceThresholdAndEvidenceRequirement() {
        LifeGraphExtractionResult result = new LifeGraphExtractionResult();
        result.setEntities(List.of(
                entity("User", "我", "__USER__"),
                entity("Person", "fixture-person-a", "fixture-person-a"),
                entity("Item", "fixture-item-a", "fixture-item-a"),
                entity("Event", "fixture-event-a", "fixture-event-a")));
        result.setRelations(List.of(
                relation("__USER__", "fixture-person-a", "PARTNER_OF", 0.60,
                        "evidence-token-direct-a"),
                relation("fixture-person-a", "fixture-item-a", "LIKES", 0.59,
                        "evidence-token-low-a"),
                relation("fixture-person-a", "fixture-event-a", "PARTICIPATED_IN", 0.90, null)));

        LifeGraphPromotionPolicy.PromotionResult promoted =
                new LifeGraphPromotionPolicy().promote(result, Set.of());

        assertTrue(promoted.acceptedEntityKeys().contains("fixture-person-a"));
        assertFalse(promoted.acceptedEntityKeys().contains("fixture-item-a"));
        assertFalse(promoted.acceptedEntityKeys().contains("fixture-event-a"));
        assertTrue(promoted.relations().stream().anyMatch(relation ->
                "PARTNER_OF".equals(relation.getType())));
        assertFalse(promoted.relations().stream().anyMatch(relation ->
                "LIKES".equals(relation.getType())));
        assertFalse(promoted.relations().stream().anyMatch(relation ->
                "PARTICIPATED_IN".equals(relation.getType())));
    }

    @Test
    void promotesConfirmedPersonAttributesWithoutExpandingToAnotherPerson() {
        LifeGraphExtractionResult result = new LifeGraphExtractionResult();
        result.setEntities(List.of(
                entity("User", "我", "__USER__"),
                entity("Person", "fixture-person-b", "fixture-person-b"),
                entity("Item", "fixture-item-b", "fixture-item-b"),
                entity("Event", "fixture-event-b", "fixture-event-b"),
                entity("Person", "fixture-person-c", "fixture-person-c")));
        result.setRelations(List.of(
                relation("__USER__", "fixture-person-b", "PARTNER_OF", 0.90,
                        "evidence-token-person-b"),
                relation("fixture-person-b", "fixture-item-b", "LIKES", 0.90,
                        "evidence-token-item-b"),
                relation("fixture-person-b", "fixture-event-b", "PARTICIPATED_IN", 0.90,
                        "evidence-token-event-b"),
                relation("fixture-person-b", "fixture-person-c", "FRIEND_OF", 0.90,
                        "evidence-token-person-c"),
                relation("__USER__", "fixture-item-b", "MENTIONED", 0.90,
                        "evidence-token-mentioned"),
                relation("__USER__", "fixture-item-b", "MENTIONED_IN", 0.90,
                        "evidence-token-mentioned-in"),
                relation("__USER__", "fixture-item-b", "SAID", 0.90,
                        "evidence-token-said"),
                relation("__USER__", "fixture-item-b", "RELATED_TO", 0.90,
                        "evidence-token-related-to")));

        LifeGraphPromotionPolicy.PromotionResult promoted =
                new LifeGraphPromotionPolicy().promote(result, Set.of("fixture-person-b"));

        assertTrue(promoted.acceptedEntityKeys().containsAll(Set.of(
                "fixture-person-b", "fixture-item-b", "fixture-event-b")));
        assertFalse(promoted.acceptedEntityKeys().contains("fixture-person-c"));
        assertTrue(promoted.relations().stream().noneMatch(relation ->
                "fixture-person-c".equals(relation.getSource())
                        || "fixture-person-c".equals(relation.getTarget())));
        assertTrue(promoted.relations().stream().noneMatch(relation ->
                Set.of("MENTIONED", "MENTIONED_IN", "SAID", "RELATED_TO")
                        .contains(relation.getType())));
    }

    @Test
    void countsDuplicateAcceptedRelationOccurrencesByNormalizedKey() {
        LifeGraphExtractionResult result = new LifeGraphExtractionResult();
        result.setEntities(List.of(
                entity("User", "我", "__USER__"),
                entity("Person", "fixture-person-a", "fixture-person-a")));
        result.setRelations(List.of(
                relation("我", "fixture-person-a", "partner_of", 0.90,
                        "evidence-token-duplicate-a"),
                relation("__USER__", "FIXTURE-PERSON-A", "PARTNER_OF", 0.90,
                        "evidence-token-duplicate-b")));

        LifeGraphPromotionPolicy.PromotionResult promoted =
                new LifeGraphPromotionPolicy().promote(result, Set.of());

        assertEquals(1, promoted.relations().size());
        assertEquals(2, promoted.relationOccurrences()
                .get("__user__|fixture-person-a|PARTNER_OF"));
    }

    @Test
    void promotesImportantPersonAndOneHopAttributeButRejectsPersonExpansion() {
        LifeGraphExtractionResult result = new LifeGraphExtractionResult();
        result.setEntities(List.of(
                entity("User", "我", "__user__"),
                entity("Person", "小美", "xiaomei"),
                entity("Item", "草莓", "strawberry"),
                entity("Person", "小王", "xiaowang")));
        result.setRelations(List.of(
                relation("__USER__", "xiaomei", "PARTNER_OF", "我是小美的伴侣"),
                relation("xiaomei", "strawberry", "LIKES", "小美喜欢草莓"),
                relation("xiaomei", "xiaowang", "HAS_COLLEAGUE", "小美的同事是小王")));

        LifeGraphPromotionPolicy.PromotionResult promoted =
                new LifeGraphPromotionPolicy().promote(result, Set.of());

        assertEquals(2, promoted.relations().size());
        assertTrue(promoted.acceptedEntityKeys().contains("xiaomei"));
        assertTrue(promoted.acceptedEntityKeys().contains("strawberry"));
        assertFalse(promoted.acceptedEntityKeys().contains("xiaowang"));
    }

    @Test
    void rejectsLanguageOnlyAndGenericRelationsWithoutLongTermSemantics() {
        LifeGraphExtractionResult result = new LifeGraphExtractionResult();
        result.setEntities(List.of(
                entity("User", "我", "__user__"),
                entity("Person", "小美", "xiaomei"),
                entity("Topic", "电影", "movie")));
        result.setRelations(List.of(
                relation("__USER__", "xiaomei", "MENTIONED", "提到了小美"),
                relation("xiaomei", "movie", "SAID", "小美说电影"),
                relation("__USER__", "movie", "RELATED_TO", "用户与电影有关")));

        LifeGraphPromotionPolicy.PromotionResult promoted =
                new LifeGraphPromotionPolicy().promote(result, Set.of("xiaomei"));

        assertTrue(promoted.relations().isEmpty());
        assertEquals(Set.of(), promoted.acceptedEntityKeys());
    }

    @Test
    void rejectsUnknownEntityTypeInsteadOfConvertingItToTopic() {
        LifeGraphExtractionResult result = new LifeGraphExtractionResult();
        result.setEntities(List.of(
                entity("User", "我", "__user__"),
                entity("Animal", "小狗", "dog")));
        result.setRelations(List.of(
                relation("__USER__", "dog", "CARED_FOR", "我照顾小狗")));

        LifeGraphPromotionPolicy.PromotionResult promoted =
                new LifeGraphPromotionPolicy().promote(result, Set.of());

        assertTrue(promoted.relations().isEmpty());
        assertFalse(promoted.acceptedEntityKeys().contains("dog"));
    }

    @Test
    void allowsUserActionDirectedAtAnAlreadyPromotedImportantPerson() {
        LifeGraphExtractionResult result = new LifeGraphExtractionResult();
        result.setEntities(List.of(
                entity("User", "我", "__user__"),
                entity("Person", "小美", "xiaomei")));
        result.setRelations(List.of(
                relation("__USER__", "xiaomei", "PARTNER_OF", "我是小美的伴侣"),
                relation("__USER__", "xiaomei", "BOUGHT_FOR", "我给小美买了礼物")));

        LifeGraphPromotionPolicy.PromotionResult promoted =
                new LifeGraphPromotionPolicy().promote(result, Set.of());

        assertEquals(2, promoted.relations().size());
        assertTrue(promoted.acceptedEntityKeys().contains("xiaomei"));
    }

    private LifeGraphExtractionResult.ExtractedEntity entity(String type, String displayName, String nameNorm) {
        LifeGraphExtractionResult.ExtractedEntity entity = new LifeGraphExtractionResult.ExtractedEntity();
        entity.setType(type);
        entity.setDisplayName(displayName);
        entity.setNameNorm(nameNorm);
        entity.setConfidence(0.9);
        entity.setImportance(0.8);
        entity.setProps(Map.of());
        return entity;
    }

    private LifeGraphExtractionResult.ExtractedRelation relation(
            String source, String target, String type, String evidence) {
        return relation(source, target, type, 0.9, evidence);
    }

    private LifeGraphExtractionResult.ExtractedRelation relation(
            String source, String target, String type, double confidence, String evidence) {
        LifeGraphExtractionResult.ExtractedRelation relation =
                new LifeGraphExtractionResult.ExtractedRelation();
        relation.setSource(source);
        relation.setTarget(target);
        relation.setType(type);
        relation.setConfidence(confidence);
        relation.setEvidenceSnippet(evidence);
        relation.setProps(Map.of());
        return relation;
    }
}
