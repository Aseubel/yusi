package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphMention;
import com.aseubel.yusi.repository.LifeGraphEntityAliasRepository;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphMentionRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
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
    private LifeGraphEntityAliasRepository aliasRepository;

    @Mock
    private LifeGraphRelationRepository relationRepository;

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
        when(entityRepository.findAllById(any())).thenReturn(List.of(entity));
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

    private LifeGraphQueryService service() {
        return new LifeGraphQueryService(entityRepository, aliasRepository, relationRepository,
                mentionRepository, new ObjectMapper());
    }
}
