package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LifeGraphQueryServiceTest {

    @Mock
    private LifeGraphEntityRepository entityRepository;

    @Mock
    private LifeGraphEntityEvidenceRepository entityEvidenceRepository;

    @Mock
    private LifeGraphEntityAliasRepository aliasRepository;

    @Mock
    private LifeGraphRelationRepository relationRepository;

    @Mock
    private LifeGraphRelationEvidenceRepository evidenceRepository;

    @Mock
    private LifeGraphMentionRepository mentionRepository;

    @Test
    void localSearchReturnsSafeSourceMetadataWithoutMentionSnippet() {
        LifeGraphEntity entity = LifeGraphEntity.builder()
                .id(11L)
                .userId("user-1")
                .type(LifeGraphEntity.EntityType.Topic)
                .nameNorm("topic")
                .displayName("Topic")
                .mentionCount(1)
                .build();
        LifeGraphMention mention = LifeGraphMention.builder()
                .userId("user-1")
                .entityId(11L)
                .diaryId("diary-7")
                .entryDate(LocalDate.of(2026, 8, 1))
                .snippet("private original diary text")
                .build();

        when(aliasRepository.findByUserIdAndAliasNorm("user-1", "topic"))
                .thenReturn(Optional.empty());
        when(entityRepository.findVisibleByUserIdAndDisplayNameContainingOrderByMentionCountDesc(
                eq("user-1"), eq("topic"), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(relationRepository.findTop200ByUserIdAndSourceIdOrderByUpdatedAtDesc("user-1", 11L))
                .thenReturn(List.of());
        when(relationRepository.findTop200ByUserIdAndTargetIdOrderByUpdatedAtDesc("user-1", 11L))
                .thenReturn(List.of());
        when(mentionRepository.findTop200ByUserIdAndEntityIdOrderByCreatedAtDesc("user-1", 11L))
                .thenReturn(List.of(mention));

        String result = service().localSearch("user-1", "topic", 5, 20, 5);

        assertTrue(result.contains("diary-7"));
        assertTrue(result.contains("2026-08-01"));
        assertFalse(result.contains("private original diary text"));
    }

    @Test
    void localSearchReturnsSafePlazaEntitySourceMetadata() {
        LifeGraphEntity entity = LifeGraphEntity.builder()
                .id(12L)
                .userId("user-1")
                .type(LifeGraphEntity.EntityType.Person)
                .nameNorm("xiaomei")
                .displayName("小美")
                .mentionCount(1)
                .build();
        LifeGraphEntityEvidence evidence = LifeGraphEntityEvidence.builder()
                .userId("user-1")
                .entityId(12L)
                .sourceType("PLAZA")
                .sourceId("42")
                .occurrenceCount(1)
                .sourceTime(LocalDateTime.of(2026, 8, 12, 20, 0))
                .snippet("private plaza text")
                .build();

        when(aliasRepository.findByUserIdAndAliasNorm("user-1", "xiaomei"))
                .thenReturn(Optional.empty());
        when(entityRepository.findVisibleByUserIdAndDisplayNameContainingOrderByMentionCountDesc(
                eq("user-1"), eq("xiaomei"), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(entityEvidenceRepository.findByUserIdAndEntityId("user-1", 12L))
                .thenReturn(List.of(evidence));
        when(mentionRepository.findTop200ByUserIdAndEntityIdOrderByCreatedAtDesc("user-1", 12L))
                .thenReturn(List.of());
        when(relationRepository.findTop200ByUserIdAndSourceIdOrderByUpdatedAtDesc("user-1", 12L))
                .thenReturn(List.of());
        when(relationRepository.findTop200ByUserIdAndTargetIdOrderByUpdatedAtDesc("user-1", 12L))
                .thenReturn(List.of());

        String result = service().localSearch("user-1", "xiaomei", 5, 20, 5);

        assertTrue(result.contains("source=PLAZA:42"));
        assertFalse(result.contains("private plaza text"));
    }

    @Test
    void localSearchTraversesMoreThanOneHopAndKeepsUserNode() {
        LifeGraphEntity user = entity(1L, LifeGraphEntity.EntityType.User, "__user__", "我");
        LifeGraphEntity person = entity(2L, LifeGraphEntity.EntityType.Person, "xiaomei", "小美");
        LifeGraphEntity trip = entity(3L, LifeGraphEntity.EntityType.Event, "trip", "第一次旅行");
        LifeGraphEntity place = entity(4L, LifeGraphEntity.EntityType.Place, "kyoto", "京都");

        LifeGraphRelation userPerson = relation(11L, 1L, 2L, 1L, 2L, "PARTNER_OF");
        LifeGraphRelation personTrip = relation(12L, 2L, 3L, 2L, 3L, "PARTICIPATED_IN");
        LifeGraphRelation tripPlace = relation(13L, 3L, 4L, 3L, 4L, "HAPPENED_AT");
        LifeGraphRelation language = relation(14L, 2L, 4L, 2L, 4L, "MENTIONED");

        when(aliasRepository.findByUserIdAndAliasNorm("user-1", "xiaomei"))
                .thenReturn(Optional.of(alias(2L)));
        when(entityRepository.findByIdAndUserId(1L, "user-1")).thenReturn(Optional.of(user));
        when(entityRepository.findByIdAndUserId(2L, "user-1")).thenReturn(Optional.of(person));
        when(entityRepository.findByIdAndUserId(3L, "user-1")).thenReturn(Optional.of(trip));
        when(entityRepository.findByIdAndUserId(4L, "user-1")).thenReturn(Optional.of(place));
        when(entityRepository.findVisibleByUserIdAndDisplayNameContainingOrderByMentionCountDesc(
                eq("user-1"), eq("xiaomei"), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(person)));
        when(entityRepository.findAllById(any())).thenReturn(List.of(user, person, trip, place));

        when(relationRepository.findTop200ByUserIdAndSourceIdOrderByUpdatedAtDesc("user-1", 2L))
                .thenReturn(List.of(userPerson, personTrip, language));
        when(relationRepository.findTop200ByUserIdAndTargetIdOrderByUpdatedAtDesc("user-1", 2L))
                .thenReturn(List.of(userPerson));
        when(relationRepository.findTop200ByUserIdAndSourceIdOrderByUpdatedAtDesc("user-1", 1L))
                .thenReturn(List.of(userPerson));
        when(relationRepository.findTop200ByUserIdAndTargetIdOrderByUpdatedAtDesc("user-1", 1L))
                .thenReturn(List.of());
        when(relationRepository.findTop200ByUserIdAndSourceIdOrderByUpdatedAtDesc("user-1", 3L))
                .thenReturn(List.of(tripPlace));
        when(relationRepository.findTop200ByUserIdAndTargetIdOrderByUpdatedAtDesc("user-1", 3L))
                .thenReturn(List.of(personTrip));
        when(relationRepository.findTop200ByUserIdAndSourceIdOrderByUpdatedAtDesc("user-1", 4L))
                .thenReturn(List.of());
        when(relationRepository.findTop200ByUserIdAndTargetIdOrderByUpdatedAtDesc("user-1", 4L))
                .thenReturn(List.of(tripPlace, language));

        when(evidenceRepository.findByUserIdAndRelationId("user-1", 11L))
                .thenReturn(List.of(evidence(11L)));
        when(evidenceRepository.findByUserIdAndRelationId("user-1", 12L))
                .thenReturn(List.of(evidence(12L)));
        when(evidenceRepository.findByUserIdAndRelationId("user-1", 13L))
                .thenReturn(List.of(evidence(13L)));

        String result = service().localSearch("user-1", "xiaomei", 10, 20, 5);

        assertTrue(result.contains("User: \u6211"));
        assertTrue(result.contains("Person: \u5c0f\u7f8e"));
        assertTrue(result.contains("Event: \u7b2c\u4e00\u6b21\u65c5\u884c"));
        assertTrue(result.contains("Place: \u4eac\u90fd"));
        assertTrue(result.contains("\u6211 -> \u5c0f\u7f8e [PARTNER_OF]"));
        assertTrue(result.contains("\u5c0f\u7f8e -> \u7b2c\u4e00\u6b21\u65c5\u884c [PARTICIPATED_IN]"));
        assertTrue(result.contains("\u7b2c\u4e00\u6b21\u65c5\u884c -> \u4eac\u90fd [HAPPENED_AT]"));
        assertFalse(result.contains("[MENTIONED]"));
    }

    @Test
    void localSearchRejectsAutomaticRelationBelowConfidenceThreshold() {
        LifeGraphEntity seed = entity(11L, LifeGraphEntity.EntityType.Person, "xiaomei", "小美");
        LifeGraphEntity item = entity(12L, LifeGraphEntity.EntityType.Item, "strawberry", "草莓");
        LifeGraphRelation lowConfidence = relation(21L, 11L, 12L, 11L, 12L, "LIKES");
        lowConfidence.setConfidence(BigDecimal.valueOf(0.5));

        when(aliasRepository.findByUserIdAndAliasNorm("user-1", "xiaomei"))
                .thenReturn(Optional.empty());
        when(entityRepository.findVisibleByUserIdAndDisplayNameContainingOrderByMentionCountDesc(
                eq("user-1"), eq("xiaomei"), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(seed)));
        when(relationRepository.findTop200ByUserIdAndSourceIdOrderByUpdatedAtDesc("user-1", 11L))
                .thenReturn(List.of(lowConfidence));

        String result = service().localSearch("user-1", "xiaomei", 5, 5, 0);

        assertFalse(result.contains("Item: 草莓"));
        assertFalse(result.contains("[LIKES]"));
    }

    private LifeGraphQueryService service() {
        return new LifeGraphQueryService(entityRepository, aliasRepository, relationRepository,
                mentionRepository, evidenceRepository, entityEvidenceRepository, new ObjectMapper());
    }

    private LifeGraphEntity entity(Long id, LifeGraphEntity.EntityType type, String norm, String displayName) {
        return LifeGraphEntity.builder().id(id).userId("user-1").type(type).nameNorm(norm)
                .displayName(displayName).mentionCount(1).hidden(false).build();
    }

    private LifeGraphRelation relation(Long id, Long sourceId, Long targetId,
                                       Long semanticSourceId, Long semanticTargetId, String type) {
        return LifeGraphRelation.builder().id(id).userId("user-1").sourceId(sourceId).targetId(targetId)
                .semanticSourceId(semanticSourceId).semanticTargetId(semanticTargetId).type(type)
                .origin(LifeGraphRelation.Origin.AUTO).weight(1).confidence(BigDecimal.valueOf(0.9))
                .build();
    }

    private LifeGraphRelationEvidence evidence(Long relationId) {
        return LifeGraphRelationEvidence.builder().userId("user-1").relationId(relationId)
                .sourceType("DIARY").sourceId("diary-1").occurrenceCount(1)
                .confidence(BigDecimal.valueOf(0.9)).build();
    }

    private com.aseubel.yusi.pojo.entity.LifeGraphEntityAlias alias(Long entityId) {
        return com.aseubel.yusi.pojo.entity.LifeGraphEntityAlias.builder().userId("user-1")
                .entityId(entityId).aliasNorm("xiaomei").aliasDisplay("小美").build();
    }
}
