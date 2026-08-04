package com.aseubel.yusi.service.match;

import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.MatchProfile;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
