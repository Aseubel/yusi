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
        LifeGraphExtractionResult.ExtractedRelation relation =
                new LifeGraphExtractionResult.ExtractedRelation();
        relation.setSource(source);
        relation.setTarget(target);
        relation.setType(type);
        relation.setConfidence(0.9);
        relation.setEvidenceSnippet(evidence);
        relation.setProps(Map.of());
        return relation;
    }
}
