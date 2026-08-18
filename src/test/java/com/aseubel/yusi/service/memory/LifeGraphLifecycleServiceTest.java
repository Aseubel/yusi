package com.aseubel.yusi.service.memory;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.pojo.dto.memory.LifeGraphMemoryItem;
import com.aseubel.yusi.pojo.dto.memory.LifeGraphMemoryResponse;
import com.aseubel.yusi.pojo.dto.memory.UpdateLifeGraphMemoryRequest;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphEntityEvidence;
import com.aseubel.yusi.pojo.entity.LifeGraphMention;
import com.aseubel.yusi.pojo.entity.LifeGraphRelation;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.repository.LifeGraphEntityAliasRepository;
import com.aseubel.yusi.repository.LifeGraphEntityEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphMentionRepository;
import com.aseubel.yusi.repository.LifeGraphMergeJudgmentRepository;
import com.aseubel.yusi.repository.LifeGraphRelationEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.service.lifegraph.constant.LifeGraphConstants;
import com.aseubel.yusi.service.match.MatchProfileAssembler;
import com.aseubel.yusi.service.security.SecurityAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LifeGraphLifecycleServiceTest {

    @Mock
    private LifeGraphEntityRepository entityRepository;

    @Mock
    private LifeGraphEntityEvidenceRepository entityEvidenceRepository;

    @Mock
    private LifeGraphEntityAliasRepository aliasRepository;

    @Mock
    private LifeGraphMentionRepository mentionRepository;

    @Mock
    private LifeGraphRelationRepository relationRepository;

    @Mock
    private LifeGraphRelationEvidenceRepository evidenceRepository;

    @Mock
    private DiaryRepository diaryRepository;

    @Mock
    private LifeGraphMergeJudgmentRepository mergeJudgmentRepository;

    @Mock
    private MatchProfileAssembler matchProfileAssembler;

    @Mock
    private SecurityAuditService securityAuditService;

    @Test
    void listProjectsDirectPersonRelationAndImportance() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 18, 12, 0);
        LifeGraphEntity userEntity = LifeGraphEntity.builder()
                .id(1L)
                .userId("user-1")
                .type(LifeGraphEntity.EntityType.User)
                .nameNorm(LifeGraphConstants.USER_ENTITY_NORM)
                .displayName("我")
                .mentionCount(0)
                .relationCount(1)
                .importance(1.0)
                .confidence(1.0)
                .build();
        LifeGraphEntity person = entity(11L, "user-1");
        person.setType(LifeGraphEntity.EntityType.Person);
        person.setNameNorm("fixture-person");
        person.setDisplayName("Fixture Person");
        person.setImportance(0.8);
        LifeGraphRelation relation = LifeGraphRelation.builder()
                .id(21L)
                .userId("user-1")
                .sourceId(1L)
                .targetId(11L)
                .semanticSourceId(1L)
                .semanticTargetId(11L)
                .type("PARTNER_OF")
                .origin(LifeGraphRelation.Origin.MANUAL)
                .updatedAt(now)
                .build();

        when(entityRepository.findByUserId(eq("user-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(person)));
        when(entityRepository.findByUserIdAndTypeAndNameNorm(
                "user-1", LifeGraphEntity.EntityType.User, LifeGraphConstants.USER_ENTITY_NORM))
                .thenReturn(Optional.of(userEntity));
        when(relationRepository.findByUserId("user-1")).thenReturn(List.of(relation));

        LifeGraphMemoryItem item = service().list("user-1", 50).getEntities().get(0);

        assertEquals("PARTNER_OF", item.getRelationToUser());
        assertEquals("MANUAL", item.getRelationOrigin());
        assertEquals(0.8, item.getImportance());
    }

    @Test
    void listDoesNotProjectRelationsForNonPersonOrMissingDirectFact() {
        LifeGraphEntity userEntity = LifeGraphEntity.builder()
                .id(1L)
                .userId("user-1")
                .type(LifeGraphEntity.EntityType.User)
                .nameNorm(LifeGraphConstants.USER_ENTITY_NORM)
                .displayName("我")
                .build();
        LifeGraphEntity person = entity(11L, "user-1");
        person.setType(LifeGraphEntity.EntityType.Person);
        person.setNameNorm("fixture-person");
        LifeGraphEntity topic = entity(12L, "user-1");
        LifeGraphRelation relationToTopic = LifeGraphRelation.builder()
                .id(22L)
                .userId("user-1")
                .sourceId(1L)
                .targetId(12L)
                .semanticSourceId(1L)
                .semanticTargetId(12L)
                .type("FAMILY_OF")
                .origin(LifeGraphRelation.Origin.MANUAL)
                .build();

        when(entityRepository.findByUserId(eq("user-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(person, topic)));
        lenient().when(entityRepository.findByUserIdAndTypeAndNameNorm(
                "user-1", LifeGraphEntity.EntityType.User, LifeGraphConstants.USER_ENTITY_NORM))
                .thenReturn(Optional.of(userEntity));
        lenient().when(relationRepository.findByUserId("user-1")).thenReturn(List.of(relationToTopic));

        List<LifeGraphMemoryItem> items = service().list("user-1", 50).getEntities();

        assertNull(items.get(0).getRelationToUser());
        assertNull(items.get(0).getRelationOrigin());
        assertNull(items.get(1).getRelationToUser());
        assertNull(items.get(1).getRelationOrigin());
        assertFalse(Arrays.stream(UpdateLifeGraphMemoryRequest.class.getDeclaredFields())
                .map(Field::getName)
                .anyMatch(Set.of("importance", "relationToUser", "relationOrigin")::contains));
    }

    @Test
    void listProjectsReverseSemanticPersonRelation() {
        LifeGraphEntity userEntity = LifeGraphEntity.builder()
                .id(1L)
                .userId("user-1")
                .type(LifeGraphEntity.EntityType.User)
                .nameNorm(LifeGraphConstants.USER_ENTITY_NORM)
                .displayName("我")
                .build();
        LifeGraphEntity person = entity(11L, "user-1");
        person.setType(LifeGraphEntity.EntityType.Person);
        person.setNameNorm("fixture-person");
        LifeGraphRelation relation = LifeGraphRelation.builder()
                .id(23L)
                .userId("user-1")
                .sourceId(1L)
                .targetId(11L)
                .semanticSourceId(11L)
                .semanticTargetId(1L)
                .type("FAMILY_OF")
                .origin(LifeGraphRelation.Origin.AUTO)
                .build();

        when(entityRepository.findByUserId(eq("user-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(person)));
        when(entityRepository.findByUserIdAndTypeAndNameNorm(
                "user-1", LifeGraphEntity.EntityType.User, LifeGraphConstants.USER_ENTITY_NORM))
                .thenReturn(Optional.of(userEntity));
        when(relationRepository.findByUserId("user-1")).thenReturn(List.of(relation));

        LifeGraphMemoryItem item = service().list("user-1", 50).getEntities().get(0);

        assertEquals("FAMILY_OF", item.getRelationToUser());
        assertEquals("AUTO", item.getRelationOrigin());
    }

    @Test
    void listSelectsDeterministicRelationAndRejectsForeignSemanticUserEndpoint() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 8, 18, 12, 0);
        LifeGraphEntity userEntity = LifeGraphEntity.builder()
                .id(1L)
                .userId("user-1")
                .type(LifeGraphEntity.EntityType.User)
                .nameNorm(LifeGraphConstants.USER_ENTITY_NORM)
                .displayName("我")
                .build();
        LifeGraphEntity person = entity(11L, "user-1");
        person.setType(LifeGraphEntity.EntityType.Person);
        person.setNameNorm("fixture-person");
        LifeGraphRelation manualFamily = relation(31L, 1L, 11L, "FAMILY_OF",
                LifeGraphRelation.Origin.MANUAL, timestamp);
        LifeGraphRelation manualChild = relation(30L, 1L, 11L, "CHILD_OF",
                LifeGraphRelation.Origin.MANUAL, timestamp);
        LifeGraphRelation manualChildTie = relation(29L, 1L, 11L, "CHILD_OF",
                LifeGraphRelation.Origin.MANUAL, timestamp);
        LifeGraphRelation newerAuto = relation(20L, 1L, 11L, "PARTNER_OF",
                LifeGraphRelation.Origin.AUTO, timestamp.plusDays(1));
        LifeGraphRelation foreignSemanticUser = relation(40L, 2L, 11L, "PARTNER_OF",
                LifeGraphRelation.Origin.MANUAL, timestamp.plusDays(2));

        when(entityRepository.findByUserId(eq("user-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(person)));
        when(entityRepository.findByUserIdAndTypeAndNameNorm(
                "user-1", LifeGraphEntity.EntityType.User, LifeGraphConstants.USER_ENTITY_NORM))
                .thenReturn(Optional.of(userEntity));
        when(relationRepository.findByUserId("user-1"))
                .thenReturn(List.of(manualFamily, manualChild, manualChildTie, newerAuto, foreignSemanticUser));

        LifeGraphMemoryItem item = service().list("user-1", 50).getEntities().get(0);

        assertEquals("CHILD_OF", item.getRelationToUser());
        assertEquals("MANUAL", item.getRelationOrigin());
    }

    @Test
    void listFallsBackToPhysicalRelationEndpoints() {
        LifeGraphEntity userEntity = LifeGraphEntity.builder()
                .id(1L)
                .userId("user-1")
                .type(LifeGraphEntity.EntityType.User)
                .nameNorm(LifeGraphConstants.USER_ENTITY_NORM)
                .displayName("我")
                .build();
        LifeGraphEntity person = entity(11L, "user-1");
        person.setType(LifeGraphEntity.EntityType.Person);
        person.setNameNorm("fixture-person");
        LifeGraphRelation relation = LifeGraphRelation.builder()
                .id(24L)
                .userId("user-1")
                .sourceId(1L)
                .targetId(11L)
                .type("FRIEND_OF")
                .origin(LifeGraphRelation.Origin.MANUAL)
                .build();

        when(entityRepository.findByUserId(eq("user-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(person)));
        when(entityRepository.findByUserIdAndTypeAndNameNorm(
                "user-1", LifeGraphEntity.EntityType.User, LifeGraphConstants.USER_ENTITY_NORM))
                .thenReturn(Optional.of(userEntity));
        when(relationRepository.findByUserId("user-1")).thenReturn(List.of(relation));

        LifeGraphMemoryItem item = service().list("user-1", 50).getEntities().get(0);

        assertEquals("FRIEND_OF", item.getRelationToUser());
        assertEquals("MANUAL", item.getRelationOrigin());
    }

    @Test
    void listIgnoresUnknownRelationTypeAndMissingOrigin() {
        LifeGraphEntity userEntity = LifeGraphEntity.builder()
                .id(1L)
                .userId("user-1")
                .type(LifeGraphEntity.EntityType.User)
                .nameNorm(LifeGraphConstants.USER_ENTITY_NORM)
                .displayName("我")
                .build();
        LifeGraphEntity person = entity(11L, "user-1");
        person.setType(LifeGraphEntity.EntityType.Person);
        person.setNameNorm("fixture-person");
        LifeGraphRelation unknownType = relation(25L, 1L, 11L, "FUTURE_RELATION",
                LifeGraphRelation.Origin.MANUAL, null);
        LifeGraphRelation missingOrigin = relation(26L, 1L, 11L, "FRIEND_OF", null, null);

        when(entityRepository.findByUserId(eq("user-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(person)));
        when(entityRepository.findByUserIdAndTypeAndNameNorm(
                "user-1", LifeGraphEntity.EntityType.User, LifeGraphConstants.USER_ENTITY_NORM))
                .thenReturn(Optional.of(userEntity));
        when(relationRepository.findByUserId("user-1"))
                .thenReturn(List.of(unknownType, missingOrigin));

        LifeGraphMemoryItem item = service().list("user-1", 50).getEntities().get(0);

        assertNull(item.getRelationToUser());
        assertNull(item.getRelationOrigin());
        assertFalse(Arrays.stream(LifeGraphMemoryItem.class.getDeclaredFields())
                .map(Field::getName)
                .anyMatch(Set.of("evidenceSnippet", "snippet", "props", "evidenceDiaryId")::contains));
    }

    @Test
    void listResolvesDiarySourceTitleForLifeGraphSources() {
        LifeGraphEntity entity = entity(11L, "user-1");
        LifeGraphMention mention = LifeGraphMention.builder()
                .userId("user-1")
                .entityId(11L)
                .diaryId("diary-7")
                .entryDate(LocalDate.of(2026, 8, 1))
                .createdAt(LocalDateTime.now())
                .build();
        when(entityRepository.findByUserId(eq("user-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mentionRepository.findTop200ByUserIdAndEntityIdOrderByCreatedAtDesc("user-1", 11L))
                .thenReturn(List.of(mention));
        when(diaryRepository.findByDiaryIdAndUserId("diary-7", "user-1"))
                .thenReturn(Diary.builder().diaryId("diary-7").userId("user-1").title("一次重要的旅行").build());

        LifeGraphMemoryResponse result = service().list("user-1", 50);

        assertEquals("一次重要的旅行", result.getEntities().get(0).getSources().get(0).getSourceTitle());
        verify(diaryRepository).findByDiaryIdAndUserId("diary-7", "user-1");
    }

    @Test
    void listReturnsDiaryReferencesWithoutMentionSnippets() {
        LifeGraphEntity entity = entity(11L, "user-1");
        LifeGraphMention mention = LifeGraphMention.builder()
                .userId("user-1")
                .entityId(11L)
                .diaryId("diary-7")
                .snippet("private original diary text")
                .entryDate(LocalDate.of(2026, 8, 1))
                .createdAt(LocalDateTime.now())
                .build();
        when(entityRepository.findByUserId(eq("user-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mentionRepository.findTop200ByUserIdAndEntityIdOrderByCreatedAtDesc("user-1", 11L))
                .thenReturn(List.of(mention));

        LifeGraphMemoryResponse result = service().list("user-1", 50);

        LifeGraphMemoryItem item = result.getEntities().get(0);
        assertEquals("diary-7", item.getSources().get(0).getSourceId());
        assertEquals("DIARY", item.getSources().get(0).getSourceType());
        assertEquals(LocalDate.of(2026, 8, 1), item.getSources().get(0).getEntryDate());
        assertFalse(Arrays.stream(item.getSources().get(0).getClass().getDeclaredFields())
                .map(Field::getName)
                .anyMatch("snippet"::equals));
    }

    @Test
    void listUsesEntityEvidenceForDiaryTitleAndPlazaSafeLabel() {
        LifeGraphEntity entity = entity(11L, "user-1");
        entity.setImportance(0.9);
        when(entityRepository.findByUserId(eq("user-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(entityEvidenceRepository.findByUserIdAndEntityId("user-1", 11L))
                .thenReturn(List.of(
                        LifeGraphEntityEvidence.builder()
                                .userId("user-1")
                                .entityId(11L)
                                .sourceType("DIARY")
                                .sourceId("diary-7")
                                .entryDate(LocalDate.of(2026, 8, 1))
                                .build(),
                        LifeGraphEntityEvidence.builder()
                                .userId("user-1")
                                .entityId(11L)
                                .sourceType("PLAZA")
                                .sourceId("42")
                                .snippet("private plaza text")
                                .build()));
        when(diaryRepository.findByDiaryIdAndUserId("diary-7", "user-1"))
                .thenReturn(Diary.builder().diaryId("diary-7").userId("user-1").title("京都旅行").build());

        LifeGraphMemoryItem item = service().list("user-1", 50).getEntities().get(0);

        assertEquals(0.9, item.getImportance());
        assertEquals("京都旅行", item.getSources().get(0).getSourceTitle());
        assertEquals("广场卡片 #42", item.getSources().get(1).getSourceTitle());
        assertFalse(Arrays.stream(item.getSources().get(1).getClass().getDeclaredFields())
                .map(Field::getName)
                .anyMatch("snippet"::equals));
        verify(diaryRepository).findByDiaryIdAndUserId("diary-7", "user-1");
    }

    @Test
    void updateChangesLifecycleAndRefreshesMatchProfile() {
        LifeGraphEntity entity = entity(11L, "user-1");
        when(entityRepository.findByIdAndUserId(11L, "user-1")).thenReturn(Optional.of(entity));
        when(entityRepository.save(any(LifeGraphEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateLifeGraphMemoryRequest request = new UpdateLifeGraphMemoryRequest();
        request.setHidden(true);
        request.setMatchAllowed(false);
        request.setConfidence(0.9);

        LifeGraphMemoryItem result = service().update("user-1", 11L, request);

        assertEquals("HIDDEN", result.getLifecycleStatus());
        assertEquals(0.9, result.getConfidence());
        verify(matchProfileAssembler).refreshProfile("user-1");
    }

    @Test
    void updateRejectsCrossUserEntityMutation() {
        when(entityRepository.findByIdAndUserId(11L, "user-1")).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> service().update("user-1", 11L, new UpdateLifeGraphMemoryRequest()));

        verify(entityRepository, never()).save(any(LifeGraphEntity.class));
        verifyNoInteractions(matchProfileAssembler);
        verify(securityAuditService).record(any());
    }

    @Test
    void listReportsExpiredEntity() {
        LifeGraphEntity entity = entity(11L, "user-1");
        entity.setValidUntil(LocalDateTime.now().minusMinutes(1));
        when(entityRepository.findByUserId(eq("user-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        LifeGraphMemoryResponse result = service().list("user-1", 50);

        assertEquals("EXPIRED", result.getEntities().get(0).getLifecycleStatus());
        assertEquals(1L, result.getExpiredCount());
        assertEquals(0L, result.getActiveCount());
    }

    @Test
    void deleteCleansDerivedRowsBeforeDeletingTheOwnedEntity() {
        LifeGraphEntity entity = entity(11L, "user-1");
        LifeGraphRelation relation = LifeGraphRelation.builder()
                .id(21L)
                .userId("user-1")
                .sourceId(11L)
                .targetId(12L)
                .type("TOPIC")
                .build();
        when(entityRepository.findByIdAndUserId(11L, "user-1")).thenReturn(Optional.of(entity));
        when(relationRepository.findByUserIdAndSourceIdIn("user-1", List.of(11L)))
                .thenReturn(List.of(relation));
        when(relationRepository.findByUserIdAndTargetIdIn("user-1", List.of(11L)))
                .thenReturn(List.of());

        service().delete("user-1", 11L);

        InOrder order = inOrder(evidenceRepository, relationRepository, aliasRepository, mentionRepository,
                mergeJudgmentRepository, entityRepository);
        order.verify(evidenceRepository).deleteByUserIdAndRelationIdIn("user-1", List.of(21L));
        order.verify(relationRepository).deleteAll(any(List.class));
        order.verify(aliasRepository).deleteByUserIdAndEntityId("user-1", 11L);
        order.verify(mentionRepository).deleteByUserIdAndEntityId("user-1", 11L);
        order.verify(mergeJudgmentRepository).deleteByUserIdAndEntityId("user-1", 11L);
        order.verify(entityRepository).delete(entity);
        verify(matchProfileAssembler).refreshProfile("user-1");
    }

    private LifeGraphLifecycleService service() {
        return new LifeGraphLifecycleService(entityRepository, entityEvidenceRepository, aliasRepository, mentionRepository,
                relationRepository, evidenceRepository, mergeJudgmentRepository, matchProfileAssembler,
                diaryRepository, securityAuditService);
    }

    private LifeGraphRelation relation(Long id, Long semanticSourceId, Long semanticTargetId,
            String type, LifeGraphRelation.Origin origin, LocalDateTime updatedAt) {
        return LifeGraphRelation.builder()
                .id(id)
                .userId("user-1")
                .sourceId(Math.min(semanticSourceId, semanticTargetId))
                .targetId(Math.max(semanticSourceId, semanticTargetId))
                .semanticSourceId(semanticSourceId)
                .semanticTargetId(semanticTargetId)
                .type(type)
                .origin(origin)
                .updatedAt(updatedAt)
                .build();
    }

    private LifeGraphEntity entity(Long id, String userId) {
        LocalDateTime now = LocalDateTime.now();
        return LifeGraphEntity.builder()
                .id(id)
                .userId(userId)
                .type(LifeGraphEntity.EntityType.Topic)
                .nameNorm("topic")
                .displayName("Topic")
                .summary("safe summary")
                .mentionCount(3)
                .relationCount(1)
                .confidence(0.7)
                .importance(0.5)
                .matchAllowed(true)
                .hidden(false)
                .createdAt(now.minusDays(2))
                .updatedAt(now)
                .build();
    }
}
