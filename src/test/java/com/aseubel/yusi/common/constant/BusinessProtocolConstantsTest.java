package com.aseubel.yusi.common.constant;

import com.aseubel.yusi.pojo.constant.AgentPersonaStyle;
import com.aseubel.yusi.pojo.constant.DiaryAttachmentAnchorKind;
import com.aseubel.yusi.pojo.constant.DiaryAttachmentType;
import com.aseubel.yusi.pojo.constant.KeyMode;
import com.aseubel.yusi.pojo.constant.MatchAction;
import com.aseubel.yusi.pojo.constant.MatchFeedbackAction;
import com.aseubel.yusi.pojo.constant.MidMemoryCategory;
import com.aseubel.yusi.pojo.constant.ProactiveFrequency;
import com.aseubel.yusi.pojo.constant.SuggestionStatus;
import com.aseubel.yusi.pojo.constant.SoulConnectionAction;
import com.aseubel.yusi.pojo.constant.SoulConnectionReason;
import com.aseubel.yusi.pojo.constant.UserLocationType;
import com.aseubel.yusi.service.ai.model.constant.ModelHealthPhase;
import com.aseubel.yusi.service.ai.model.constant.ModelProviderType;
import com.aseubel.yusi.service.ai.model.constant.ModelRouteExclusionReason;
import com.aseubel.yusi.service.cognition.constant.CognitiveConflictSource;
import com.aseubel.yusi.service.cognition.constant.MidMemoryConflictAction;
import com.aseubel.yusi.service.lifegraph.constant.LifeGraphMergeDecision;
import com.aseubel.yusi.service.lifegraph.LifeGraphPromotionPolicy;
import com.aseubel.yusi.service.lifegraph.constant.LifeGraphEvidenceKind;
import com.aseubel.yusi.service.lifegraph.constant.LifeGraphMergeStatus;
import com.aseubel.yusi.service.lifegraph.constant.LifeGraphRelationType;
import com.aseubel.yusi.service.report.constant.SoulReportType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessProtocolConstantsTest {

    @Test
    void preservesPersistedSourceCodesAndNormalizesInput() {
        assertEquals("DIARY", SourceType.DIARY.code());
        assertEquals("PLAZA", SourceType.PLAZA.code());
        assertEquals("CHAT_SUMMARY", SourceType.CHAT_SUMMARY.code());
        assertEquals("EMOTION_PLAZA", SourceType.EMOTION_PLAZA.code());
        assertEquals(SourceType.DIARY, SourceType.fromCode(" diary "));
        assertEquals(SourceType.UNKNOWN, SourceType.fromCode("not-a-source"));
    }

    @Test
    void centralizesEmotionCodesWithoutChangingModelOutput() {
        assertEquals("Neutral", EmotionType.NEUTRAL.code());
        assertEquals(EmotionType.LOVE, EmotionType.fromModelValue("love"));
        assertEquals(EmotionType.ANGER, EmotionType.fromModelValue("The answer is Anger."));
        assertEquals(Set.of("Joy", "Sadness", "Anxiety", "Love", "Anger",
                "Fear", "Hope", "Calm", "Confusion", "Neutral"), EmotionType.codes());
    }

    @Test
    void lifeGraphPolicyUsesTheSharedEntityAndRelationProtocol() {
        assertEquals(Set.of("PERSON", "EVENT", "PLACE", "EMOTION", "TOPIC", "ITEM", "WORK", "USER"),
                new LifeGraphPromotionPolicy().supportedTypes());
        assertTrue(LifeGraphRelationType.PARTNER_OF.isPersonRelation());
        assertTrue(LifeGraphRelationType.LIKES.isValueRelation());
        assertTrue(LifeGraphRelationType.MENTIONED.isRejectedForAutomaticGraph());
        assertFalse(LifeGraphRelationType.fromCode("HAS_COLLEAGUE") != null);
        assertEquals("USER", LifeGraphEvidenceKind.USER.code());
    }

    @Test
    void centralizesLifecycleAndAgentConfigurationCodes() {
        assertEquals("ACTIVE", LifecycleStatus.ACTIVE.code());
        assertEquals("EMPTY", LifecycleStatus.EMPTY.code());
        assertEquals("gentle", AgentPersonaStyle.GENTLE.code());
        assertEquals("low", ProactiveFrequency.LOW.code());
        assertEquals(ProactiveFrequency.OFF, ProactiveFrequency.fromCode("OFF"));
    }

    @Test
    void preservesMatchActionApiCodes() {
        assertEquals(MatchAction.INTERESTED, MatchAction.fromApiCode(1));
        assertEquals(MatchAction.SKIPPED, MatchAction.fromApiCode(2));
        assertEquals("ACCEPT", MatchAction.INTERESTED.feedbackCode());
        assertEquals("SKIP", MatchAction.SKIPPED.feedbackCode());
    }

    @Test
    void centralizesModelProviderAliasesAndHistoricalStatuses() {
        assertEquals("openai-compatible", ModelProviderType.fromAlias("deepseek").canonicalCode());
        assertEquals("anthropic", ModelProviderType.fromAlias("anthropic").canonicalCode());
        assertTrue(ModelCallStatus.isSuccess("SUCCEEDED"));
        assertTrue(ModelCallStatus.isSuccess("OK"));
        assertFalse(ModelCallStatus.isSuccess("FAILED"));
    }

    @Test
    void centralizesMatchFeedbackAndLifeGraphMergeStatuses() {
        assertEquals("DEEP_INTERACTION", MatchFeedbackAction.DEEP_INTERACTION.code());
        assertEquals("DO_NOT_CONTINUE", MatchFeedbackAction.DO_NOT_CONTINUE.code());
        assertEquals("PENDING", LifeGraphMergeStatus.PENDING.code());
        assertEquals("ACCEPTED", LifeGraphMergeStatus.ACCEPTED.code());
    }

    @Test
    void centralizesKeyMemoryCategoryAndSuggestionCodes() {
        assertEquals("DEFAULT", KeyMode.DEFAULT.code());
        assertEquals(KeyMode.CUSTOM, KeyMode.fromCode("custom"));
        assertEquals("EVENT_OR_PLAN", MidMemoryCategory.EVENT_OR_PLAN.code());
        assertEquals("PENDING", SuggestionStatus.PENDING.code());
        assertEquals("REPLIED", SuggestionStatus.REPLIED.code());
    }

    @Test
    void centralizesRemainingCrossClassProtocolValues() {
        assertEquals("HALF_OPEN", ModelHealthPhase.HALF_OPEN.code());
        assertEquals("fallback-tier", ModelRouteExclusionReason.FALLBACK_TIER.code());
        assertEquals("WEEKLY", SoulReportType.WEEKLY.code());
        assertEquals("PERSONA", CognitiveConflictSource.PERSONA.code());
        assertEquals(MidMemoryConflictAction.OVERWRITE_B,
                MidMemoryConflictAction.fromCode("overwrite_b"));
        assertTrue(LifeGraphMergeDecision.isYes(" yes "));
        assertEquals("ACCEPT", SoulConnectionAction.ACCEPT.code());
        assertEquals("UNSAFE", SoulConnectionReason.UNSAFE.code());
        assertEquals("FREQUENT", UserLocationType.FREQUENT.code());
        assertEquals("IMAGE", DiaryAttachmentType.IMAGE.code());
        assertEquals("TEXT_RANGE", DiaryAttachmentAnchorKind.TEXT_RANGE.code());
    }
}
