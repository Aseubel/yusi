package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphRelation;
import com.aseubel.yusi.pojo.entity.LifeGraphMention;
import com.aseubel.yusi.repository.LifeGraphEntityAliasRepository;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphMentionRepository;
import com.aseubel.yusi.repository.LifeGraphRelationEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.aseubel.yusi.service.lifegraph.dto.GraphSnapshotDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LifeGraphDataServiceVisibilityTest {

    @Mock
    private LifeGraphEntityRepository entityRepository;

    @Mock
    private LifeGraphEntityAliasRepository aliasRepository;

    @Mock
    private LifeGraphMentionRepository mentionRepository;

    @Mock
    private LifeGraphRelationRepository relationRepository;

    @Mock
    private LifeGraphRelationEvidenceRepository evidenceRepository;

    @Test
    void mergingEntitiesMovesMentionsToTheSurvivingEntity() {
        LifeGraphEntity source = entity(1L, "old-name");
        LifeGraphEntity target = entity(2L, "new-name");
        LifeGraphMention mention = LifeGraphMention.builder()
                .id(7L)
                .userId("user-1")
                .entityId(1L)
                .diaryId("diary-1")
                .build();
        when(entityRepository.findById(1L)).thenReturn(java.util.Optional.of(source));
        when(entityRepository.save(source)).thenReturn(source);
        when(entityRepository.findByUserIdAndNameNorm("user-1", "new-name"))
                .thenReturn(List.of(target));
        when(mentionRepository.findByUserIdAndEntityId("user-1", 1L)).thenReturn(List.of(mention));
        when(aliasRepository.findByUserIdAndEntityId("user-1", 1L)).thenReturn(List.of());
        when(relationRepository.findByUserIdAndSourceIdIn("user-1", List.of(1L)))
                .thenReturn(List.of());
        when(relationRepository.findByUserIdAndTargetIdIn("user-1", List.of(1L)))
                .thenReturn(List.of());

        service().updateEntity("user-1", 1L, "new-name", "Topic", null, null, null);

        assertEquals(2L, mention.getEntityId());
        verify(mentionRepository).saveAll(List.of(mention));
        verify(entityRepository).delete(source);
    }

    @Test
    void deletingEntityRemovesRelationEvidenceBeforeRelations() {
        LifeGraphEntity entity = entity(1L, "topic");
        LifeGraphRelation relation = com.aseubel.yusi.pojo.entity.LifeGraphRelation.builder()
                .id(9L)
                .userId("user-1")
                .sourceId(1L)
                .targetId(2L)
                .type("RELATED_TO")
                .build();
        when(entityRepository.findById(1L)).thenReturn(java.util.Optional.of(entity));
        when(relationRepository.findByUserIdAndSourceIdIn("user-1", List.of(1L)))
                .thenReturn(List.of(relation));
        when(relationRepository.findByUserIdAndTargetIdIn("user-1", List.of(1L)))
                .thenReturn(List.of());

        service().deleteEntity("user-1", 1L);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(evidenceRepository, relationRepository,
                entityRepository);
        order.verify(evidenceRepository).deleteByUserIdAndRelationIdIn("user-1", List.of(9L));
        order.verify(relationRepository).deleteAll(List.of(relation));
        order.verify(entityRepository).delete(entity);
    }

    @Test
    void fullGraphDoesNotReturnHiddenOrExpiredNodes() {
        LifeGraphEntity visible = entity(1L, "visible");
        when(entityRepository.findVisibleByUserId(eq("user-1"), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(visible)));
        when(entityRepository.countVisibleByUserId(eq("user-1"), any())).thenReturn(1L);
        when(relationRepository.findByUserIdAndSourceIdIn(eq("user-1"), any())).thenReturn(List.of());

        GraphSnapshotDTO snapshot = service().getFullGraph("user-1", 0, 200);

        assertEquals(List.of(1L), snapshot.getNodes().stream()
                .map(GraphSnapshotDTO.NodeDTO::getId)
                .toList());
        assertEquals(1L, snapshot.getTotalNodeCount());
        verify(entityRepository).findVisibleByUserId(eq("user-1"), any(), any(Pageable.class));
        verify(entityRepository, never()).findByUserId(eq("user-1"), any(Pageable.class));
        verify(entityRepository, never()).countByUserId("user-1");
    }

    private LifeGraphDataService service() {
        return new LifeGraphDataService(entityRepository, relationRepository, evidenceRepository,
                aliasRepository, mentionRepository);
    }

    private LifeGraphEntity entity(Long id, String name) {
        return LifeGraphEntity.builder()
                .id(id)
                .userId("user-1")
                .type(LifeGraphEntity.EntityType.Topic)
                .nameNorm(name)
                .displayName(name)
                .mentionCount(1)
                .relationCount(0)
                .build();
    }
}
