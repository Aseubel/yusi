package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.LifeGraphRelation;
import com.aseubel.yusi.pojo.entity.LifeGraphRelationEvidence;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphMention;
import com.aseubel.yusi.repository.LifeGraphEntityAliasRepository;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphMentionRepository;
import com.aseubel.yusi.repository.LifeGraphRelationEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.lifegraph.ai.LifeGraphExtractor;
import com.aseubel.yusi.service.lifegraph.impl.LifeGraphBuildServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LifeGraphSourceReplacementTest {

    @Mock
    private LifeGraphEntityRepository entityRepository;

    @Mock
    private LifeGraphEntityAliasRepository aliasRepository;

    @Mock
    private LifeGraphRelationRepository relationRepository;

    @Mock
    private LifeGraphRelationEvidenceRepository evidenceRepository;

    @Mock
    private LifeGraphMentionRepository mentionRepository;

    @Mock
    private PromptManager promptManager;

    @Mock
    private LifeGraphExtractor extractor;

    @Test
    void invalidExtractionRemovesExistingDiaryContributionsAndRaisesForRetry() {
        when(entityRepository.findVisibleByUserId(eq("user-1"), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        when(aliasRepository.findTop200ByUserIdOrderByConfidenceDesc("user-1")).thenReturn(List.of());
        when(promptManager.getPrompt(any(PromptKey.class))).thenReturn("prompt");
        when(extractor.extract(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn("not-json");

        assertThrows(IllegalStateException.class, () -> service().upsertFromDiary(diary(), "content"));

        verify(mentionRepository).deleteByUserIdAndDiaryId("user-1", "diary-1");
        verify(evidenceRepository).deleteByUserIdAndSourceTypeAndSourceId("user-1", "DIARY", "diary-1");
    }

    @Test
    void deletingOneDiaryKeepsAnAutomaticallyExtractedRelationWithAnotherSource() {
        LifeGraphRelation relation = LifeGraphRelation.builder()
                .id(9L)
                .userId("user-1")
                .sourceId(1L)
                .targetId(2L)
                .type("RELATED_TO")
                .origin(LifeGraphRelation.Origin.AUTO)
                .weight(2)
                .confidence(BigDecimal.valueOf(0.8))
                .build();
        LifeGraphRelationEvidence current = LifeGraphRelationEvidence.builder()
                .id(11L)
                .userId("user-1")
                .relationId(9L)
                .sourceType("DIARY")
                .sourceId("diary-1")
                .occurrenceCount(1)
                .build();
        LifeGraphRelationEvidence remaining = LifeGraphRelationEvidence.builder()
                .id(12L)
                .userId("user-1")
                .relationId(9L)
                .sourceType("DIARY")
                .sourceId("diary-2")
                .occurrenceCount(1)
                .build();

        when(mentionRepository.findByUserIdAndDiaryId("user-1", "diary-1")).thenReturn(List.of());
        when(evidenceRepository.findByUserIdAndSourceTypeAndSourceId("user-1", "DIARY", "diary-1"))
                .thenReturn(List.of(current));
        when(relationRepository.findByIdAndUserId(9L, "user-1")).thenReturn(java.util.Optional.of(relation));
        when(evidenceRepository.findByUserIdAndRelationId("user-1", 9L)).thenReturn(List.of(remaining));

        service().deleteByDiary("user-1", "diary-1");

        verify(evidenceRepository).deleteByUserIdAndSourceTypeAndSourceId("user-1", "DIARY", "diary-1");
        verify(relationRepository).save(relation);
        verify(relationRepository, never()).delete(relation);
        assertEquals(1, relation.getWeight());
    }

    @Test
    void duplicateRelationsFromOneDiaryUseTheExactSourceOccurrenceCount() {
        AtomicReference<LifeGraphRelation> relationHolder = new AtomicReference<>();
        AtomicReference<LifeGraphRelationEvidence> evidenceHolder = new AtomicReference<>();

        when(entityRepository.findVisibleByUserId(eq("user-1"), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        when(aliasRepository.findTop200ByUserIdOrderByConfidenceDesc("user-1")).thenReturn(List.of());
        when(mentionRepository.findByUserIdAndDiaryId("user-1", "diary-1")).thenReturn(List.of());
        when(evidenceRepository.findByUserIdAndSourceTypeAndSourceId("user-1", "DIARY", "diary-1"))
                .thenReturn(List.of());
        when(promptManager.getPrompt(any(PromptKey.class))).thenReturn("prompt");
        when(extractor.extract(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(""
                        + "{\"entities\":["
                        + "{\"type\":\"Person\",\"displayName\":\"Alice\",\"nameNorm\":\"alice\"},"
                        + "{\"type\":\"Event\",\"displayName\":\"Trip\",\"nameNorm\":\"trip\"}],"
                        + "\"relations\":["
                        + "{\"source\":\"alice\",\"target\":\"trip\",\"type\":\"RELATED_TO\"},"
                        + "{\"source\":\"alice\",\"target\":\"trip\",\"type\":\"RELATED_TO\"}],"
                        + "\"mentions\":[]}");
        when(aliasRepository.findByUserIdAndAliasNorm(anyString(), anyString())).thenReturn(Optional.empty());
        when(entityRepository.findByUserIdAndTypeAndNameNorm(anyString(), any(), anyString()))
                .thenReturn(Optional.empty());
        when(entityRepository.save(any(com.aseubel.yusi.pojo.entity.LifeGraphEntity.class)))
                .thenAnswer(invocation -> {
                    com.aseubel.yusi.pojo.entity.LifeGraphEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(entity.getNameNorm().equals("alice") ? 11L
                                : entity.getNameNorm().equals("trip") ? 12L : 1L);
                    }
                    return entity;
                });
        when(relationRepository.findByUserIdAndSourceIdAndTargetIdAndType(
                "user-1", 11L, 12L, "RELATED_TO"))
                .thenAnswer(invocation -> Optional.ofNullable(relationHolder.get()));
        when(relationRepository.save(any(LifeGraphRelation.class))).thenAnswer(invocation -> {
            LifeGraphRelation relation = invocation.getArgument(0);
            if (relation.getId() == null) {
                relation.setId(21L);
            }
            relationHolder.set(relation);
            return relation;
        });
        when(evidenceRepository.findByUserIdAndRelationIdAndSourceTypeAndSourceId(
                "user-1", 21L, "DIARY", "diary-1"))
                .thenAnswer(invocation -> Optional.ofNullable(evidenceHolder.get()));
        when(evidenceRepository.save(any(LifeGraphRelationEvidence.class))).thenAnswer(invocation -> {
            LifeGraphRelationEvidence evidence = invocation.getArgument(0);
            if (evidence.getId() == null) {
                evidence.setId(31L);
            }
            evidenceHolder.set(evidence);
            return evidence;
        });

        service().upsertFromDiary(diary(), "content");

        assertEquals(2, relationHolder.get().getWeight());
        assertEquals(2, evidenceHolder.get().getOccurrenceCount());
    }

    @Test
    void deletingDiaryDoesNotRewriteManualEntityMentionCount() {
        LifeGraphEntity manualEntity = LifeGraphEntity.builder()
                .id(41L)
                .userId("user-1")
                .type(LifeGraphEntity.EntityType.Topic)
                .nameNorm("manual-topic")
                .displayName("Manual topic")
                .mentionCount(7)
                .origin(LifeGraphEntity.Origin.MANUAL)
                .build();
        LifeGraphMention mention = LifeGraphMention.builder()
                .userId("user-1")
                .entityId(41L)
                .diaryId("diary-1")
                .build();

        when(mentionRepository.findByUserIdAndDiaryId("user-1", "diary-1")).thenReturn(List.of(mention));
        when(mentionRepository.findByUserIdAndEntityId("user-1", 41L)).thenReturn(List.of());
        when(entityRepository.findByIdAndUserId(41L, "user-1")).thenReturn(Optional.of(manualEntity));
        when(evidenceRepository.findByUserIdAndSourceTypeAndSourceId("user-1", "DIARY", "diary-1"))
                .thenReturn(List.of());

        service().deleteByDiary("user-1", "diary-1");

        assertEquals(7, manualEntity.getMentionCount());
        verify(entityRepository, never()).save(manualEntity);
    }

    @Test
    void extractedEntitiesGetDiarySourceRowsEvenWhenMentionPayloadIsEmpty() {
        AtomicReference<LifeGraphMention> mentionHolder = new AtomicReference<>();

        when(entityRepository.findVisibleByUserId(eq("user-1"), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        when(aliasRepository.findTop200ByUserIdOrderByConfidenceDesc("user-1")).thenReturn(List.of());
        when(mentionRepository.findByUserIdAndDiaryId("user-1", "diary-1")).thenReturn(List.of());
        when(evidenceRepository.findByUserIdAndSourceTypeAndSourceId("user-1", "DIARY", "diary-1"))
                .thenReturn(List.of());
        when(promptManager.getPrompt(any(PromptKey.class))).thenReturn("prompt");
        when(extractor.extract(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(""
                        + "{\"entities\":[{\"type\":\"Event\",\"displayName\":\"Trip\","
                        + "\"nameNorm\":\"trip\"}],\"relations\":[],\"mentions\":[]}");
        when(aliasRepository.findByUserIdAndAliasNorm(anyString(), anyString())).thenReturn(Optional.empty());
        when(entityRepository.findByUserIdAndTypeAndNameNorm(anyString(), any(), anyString()))
                .thenReturn(Optional.empty());
        when(entityRepository.save(any(LifeGraphEntity.class))).thenAnswer(invocation -> {
            LifeGraphEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(entity.getType() == LifeGraphEntity.EntityType.User ? 1L : 11L);
            }
            return entity;
        });
        when(mentionRepository.save(any(LifeGraphMention.class))).thenAnswer(invocation -> {
            LifeGraphMention mention = invocation.getArgument(0);
            mentionHolder.set(mention);
            return mention;
        });

        service().upsertFromDiary(diary(), "content");

        assertEquals("user-1", mentionHolder.get().getUserId());
        assertEquals(11L, mentionHolder.get().getEntityId());
        assertEquals("diary-1", mentionHolder.get().getDiaryId());
    }

    @Test
    void explicitMentionDetailsArePreservedWhenEntityWasAlsoExtracted() {
        AtomicReference<LifeGraphMention> mentionHolder = new AtomicReference<>();

        when(entityRepository.findVisibleByUserId(eq("user-1"), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        when(aliasRepository.findTop200ByUserIdOrderByConfidenceDesc("user-1")).thenReturn(List.of());
        when(mentionRepository.findByUserIdAndDiaryId("user-1", "diary-1")).thenReturn(List.of());
        when(evidenceRepository.findByUserIdAndSourceTypeAndSourceId("user-1", "DIARY", "diary-1"))
                .thenReturn(List.of());
        when(promptManager.getPrompt(any(PromptKey.class))).thenReturn("prompt");
        when(extractor.extract(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(""
                        + "{\"entities\":[{\"type\":\"Event\",\"displayName\":\"Trip\","
                        + "\"nameNorm\":\"trip\"}],\"relations\":[],"
                        + "\"mentions\":[{\"entity\":\"trip\",\"snippet\":\"Trip happened\"}]}" );
        when(aliasRepository.findByUserIdAndAliasNorm(anyString(), anyString())).thenReturn(Optional.empty());
        when(entityRepository.findByUserIdAndTypeAndNameNorm(anyString(), any(), anyString()))
                .thenReturn(Optional.empty());
        when(entityRepository.save(any(LifeGraphEntity.class))).thenAnswer(invocation -> {
            LifeGraphEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(entity.getType() == LifeGraphEntity.EntityType.User ? 1L : 11L);
            }
            return entity;
        });
        when(mentionRepository.save(any(LifeGraphMention.class))).thenAnswer(invocation -> {
            LifeGraphMention mention = invocation.getArgument(0);
            mentionHolder.set(mention);
            return mention;
        });

        service().upsertFromDiary(diary(), "content");

        assertEquals("Trip happened", mentionHolder.get().getSnippet());
    }

    @Test
    void deletingDiaryRemovesAutomaticWeightFromManualRelationButKeepsManualBaseWeight() {
        LifeGraphRelation relation = LifeGraphRelation.builder()
                .id(51L)
                .userId("user-1")
                .sourceId(1L)
                .targetId(2L)
                .type("RELATED_TO")
                .origin(LifeGraphRelation.Origin.MANUAL)
                .manualWeight(5)
                .weight(7)
                .build();
        LifeGraphRelationEvidence current = LifeGraphRelationEvidence.builder()
                .id(61L)
                .userId("user-1")
                .relationId(51L)
                .sourceType("DIARY")
                .sourceId("diary-1")
                .occurrenceCount(2)
                .build();

        when(mentionRepository.findByUserIdAndDiaryId("user-1", "diary-1")).thenReturn(List.of());
        when(evidenceRepository.findByUserIdAndSourceTypeAndSourceId("user-1", "DIARY", "diary-1"))
                .thenReturn(List.of(current));
        when(relationRepository.findByIdAndUserId(51L, "user-1")).thenReturn(Optional.of(relation));
        when(evidenceRepository.findByUserIdAndRelationId("user-1", 51L)).thenReturn(List.of(current));

        service().deleteByDiary("user-1", "diary-1");

        assertEquals(5, relation.getWeight());
        verify(relationRepository).save(relation);
    }

    private LifeGraphBuildServiceImpl service() {
        return new LifeGraphBuildServiceImpl(entityRepository, aliasRepository, relationRepository,
                evidenceRepository, mentionRepository, promptManager, extractor, new ObjectMapper());
    }

    private Diary diary() {
        return Diary.builder().userId("user-1").diaryId("diary-1").title("title").build();
    }
}
