package com.aseubel.yusi.service.match;

import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.MatchProfile;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.pojo.entity.UserPersona;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.MatchProfileRepository;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.service.match.impl.MatchProfileAssemblerImpl;
import com.aseubel.yusi.service.user.UserPersonaService;
import com.aseubel.yusi.service.user.UserService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MatchProfileAssemblerVisibilityTest {

    @Mock
    private LifeGraphEntityRepository lifeGraphEntityRepository;

    @Mock
    private MidTermMemoryRepository midTermMemoryRepository;

    @Mock
    private MatchProfileRepository matchProfileRepository;

    @Mock
    private UserPersonaService userPersonaService;

    @Mock
    private UserService userService;

    @Mock
    private MilvusClientV2 milvusClient;

    @Mock
    private EmbeddingModel embeddingModel;

    @Test
    void matchProfileOnlyUsesExplicitlyMatchablePersonaAndGraphData() {
        when(userService.getUserByUserId("user-1")).thenReturn(User.builder().userId("user-1").build());
        when(lifeGraphEntityRepository.findMatchableTopByUserId(anyString(), any(), any()))
                .thenReturn(List.of(entity(1L, "allowed")));
        when(userPersonaService.getMatchableUserPersona("user-1"))
                .thenReturn(UserPersona.builder().userId("user-1").interests("允许兴趣").build());
        when(midTermMemoryRepository.findMatchableByUserId(anyString(), any(), any()))
                .thenReturn(List.of());
        when(matchProfileRepository.findByUserId("user-1")).thenReturn(Optional.empty());
        when(matchProfileRepository.save(any(MatchProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(embeddingModel.embed(anyString())).thenReturn(dev.langchain4j.model.output.Response.from(
                Embedding.from(new float[] { 0.1f })));

        MatchProfile profile = service().refreshProfile("user-1");

        assertFalse(profile.getLifeGraphSummary().contains("hidden"));
        assertFalse(profile.getPersonaSummary().contains("hidden"));
        verify(lifeGraphEntityRepository).findMatchableTopByUserId(anyString(), any(), any());
        verify(userPersonaService).getMatchableUserPersona("user-1");
        verify(lifeGraphEntityRepository, never()).findTop50ByUserIdOrderByMentionCountDesc("user-1");
    }

    @Test
    void lifeGraphSummaryPrefersImportanceOverMentionCountWithinType() {
        LifeGraphEntity mentionHeavy = entity(1L, "fixture-mention-heavy");
        mentionHeavy.setImportance(0.4);
        mentionHeavy.setMentionCount(9);

        LifeGraphEntity importanceHeavy = entity(2L, "fixture-importance-heavy");
        importanceHeavy.setImportance(0.8);
        importanceHeavy.setMentionCount(1);

        MatchProfile profile = refreshProfile(List.of(mentionHeavy, importanceHeavy), List.of());

        assertOrdered(profile.getLifeGraphSummary(), "fixture-importance-heavy", "fixture-mention-heavy");
    }

    @Test
    void lifeGraphSummaryUsesMentionCountAsTieBreakerWhenImportanceMatches() {
        LifeGraphEntity lowMention = entity(3L, "fixture-mention-low");
        lowMention.setImportance(0.6);
        lowMention.setMentionCount(1);

        LifeGraphEntity highMention = entity(4L, "fixture-mention-high");
        highMention.setImportance(0.6);
        highMention.setMentionCount(4);

        MatchProfile profile = refreshProfile(List.of(lowMention, highMention), List.of());

        assertOrdered(profile.getLifeGraphSummary(), "fixture-mention-high", "fixture-mention-low");
    }

    @Test
    void lifeGraphSummaryUsesUpdatedAtThenIdAsStableTieBreakers() {
        LocalDateTime newer = LocalDateTime.of(2026, 8, 17, 12, 0);
        LocalDateTime older = newer.minusDays(1);

        LifeGraphEntity olderEntity = entity(19L, "fixture-updated-old");
        olderEntity.setImportance(0.7);
        olderEntity.setMentionCount(2);
        olderEntity.setUpdatedAt(older);

        LifeGraphEntity newerEntity = entity(20L, "fixture-updated-new");
        newerEntity.setImportance(0.7);
        newerEntity.setMentionCount(2);
        newerEntity.setUpdatedAt(newer);

        LifeGraphEntity highId = entity(12L, "fixture-id-high");
        highId.setImportance(0.7);
        highId.setMentionCount(2);
        highId.setUpdatedAt(newer);

        LifeGraphEntity lowId = entity(11L, "fixture-id-low");
        lowId.setImportance(0.7);
        lowId.setMentionCount(2);
        lowId.setUpdatedAt(newer);

        MatchProfile profile = refreshProfile(
                List.of(olderEntity, highId, newerEntity, lowId), List.of());

        assertOrdered(profile.getLifeGraphSummary(), "fixture-updated-new", "fixture-updated-old");
        assertOrdered(profile.getLifeGraphSummary(), "fixture-id-low", "fixture-id-high");
    }

    @Test
    void midTermMemorySummaryStillUsesDecayedImportance() {
        LocalDateTime now = LocalDateTime.now();
        MidTermMemory recent = memory("fixture-mid-recent", 0.7, now.minusHours(1));
        MidTermMemory old = memory("fixture-mid-old", 1.0, now.minusDays(365));

        MatchProfile profile = refreshProfile(List.of(), List.of(old, recent));

        assertOrdered(profile.getMidMemorySummary(), "fixture-mid-recent", "fixture-mid-old");
    }

    private MatchProfile refreshProfile(List<LifeGraphEntity> entities, List<MidTermMemory> memories) {
        when(userService.getUserByUserId("user-1")).thenReturn(User.builder().userId("user-1").build());
        when(lifeGraphEntityRepository.findMatchableTopByUserId(anyString(), any(), any()))
                .thenReturn(entities);
        when(userPersonaService.getMatchableUserPersona("user-1"))
                .thenReturn(UserPersona.builder().userId("user-1").build());
        when(midTermMemoryRepository.findMatchableByUserId(anyString(), any(), any()))
                .thenReturn(memories);
        when(matchProfileRepository.findByUserId("user-1")).thenReturn(Optional.empty());
        when(matchProfileRepository.save(any(MatchProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(embeddingModel.embed(anyString())).thenReturn(dev.langchain4j.model.output.Response.from(
                Embedding.from(new float[] { 0.1f })));

        return service().refreshProfile("user-1");
    }

    private MidTermMemory memory(String summary, double importance, LocalDateTime createdAt) {
        return MidTermMemory.builder()
                .userId("user-1")
                .sourceType("FIXTURE")
                .sourceId("fixture-source")
                .summary(summary)
                .importance(importance)
                .confidence(0.9)
                .matchAllowed(true)
                .hidden(false)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }

    private void assertOrdered(String value, String first, String second) {
        assertTrue(value.contains(first));
        assertTrue(value.contains(second));
        assertTrue(value.indexOf(first) < value.indexOf(second),
                () -> "expected %s before %s in %s".formatted(first, second, value));
    }

    private MatchProfileAssemblerImpl service() {
        return new MatchProfileAssemblerImpl(lifeGraphEntityRepository, midTermMemoryRepository,
                matchProfileRepository, userPersonaService, userService, milvusClient, embeddingModel);
    }

    private LifeGraphEntity entity(Long id, String name) {
        return LifeGraphEntity.builder()
                .id(id)
                .userId("user-1")
                .type(LifeGraphEntity.EntityType.Topic)
                .nameNorm(name)
                .displayName(name)
                .summary("allowed summary")
                .mentionCount(1)
                .relationCount(0)
                .build();
    }
}
