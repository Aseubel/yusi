package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
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
    private LifeGraphRelationRepository relationRepository;

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
        return new LifeGraphDataService(entityRepository, relationRepository);
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
