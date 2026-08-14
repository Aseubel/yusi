package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.repository.LifeGraphEntityAliasRepository;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphMentionRepository;
import com.aseubel.yusi.repository.LifeGraphRelationEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.ai.prompt.PromptSnapshot;
import com.aseubel.yusi.service.lifegraph.ai.LifeGraphExtractor;
import com.aseubel.yusi.service.lifegraph.impl.LifeGraphBuildServiceImpl;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LifeGraphBuildServiceTest {

    @Mock
    private LifeGraphEntityRepository entityRepository;

    @Mock
    private LifeGraphEntityAliasRepository aliasRepository;

    @Mock
    private LifeGraphRelationRepository relationRepository;

    @Mock
    private LifeGraphMentionRepository mentionRepository;

    @Mock
    private LifeGraphRelationEvidenceRepository evidenceRepository;

    @Mock
    private PromptManager promptManager;

    @Mock
    private LifeGraphExtractor extractor;

    @Test
    void extractionReusesCanonicalUserEntityForUserSentinel() {
        LifeGraphEntity userEntity = LifeGraphEntity.builder()
                .id(1L)
                .userId("user-1")
                .type(LifeGraphEntity.EntityType.User)
                .nameNorm("__user__")
                .displayName("我")
                .mentionCount(0)
                .build();

        when(entityRepository.findVisibleByUserId(eq("user-1"), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(aliasRepository.findTop200ByUserIdOrderByConfidenceDesc("user-1"))
                .thenReturn(List.of());
        when(mentionRepository.findByUserIdAndDiaryId("user-1", "diary-1"))
                .thenReturn(List.of());
        when(promptManager.getSnapshot(any(PromptKey.class)))
                .thenReturn(new PromptSnapshot("graphrag-extract", "test", "zh-CN", ""));
        when(extractor.extract(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString()))
                .thenReturn("{\"entities\":[{\"type\":\"User\",\"displayName\":\"我\","
                        + "\"nameNorm\":\"__USER__\"}],\"relations\":[],\"mentions\":[]}");
        when(entityRepository.findByUserIdAndTypeAndNameNorm("user-1", LifeGraphEntity.EntityType.User,
                "__user__")).thenReturn(Optional.of(userEntity));

        service().upsertFromDiary(Diary.builder()
                .userId("user-1")
                .diaryId("diary-1")
                .title("title")
                .entryDate(LocalDate.of(2026, 8, 1))
                .build(), "diary content");

        verify(entityRepository, never()).save(any(LifeGraphEntity.class));
    }

    private LifeGraphBuildServiceImpl service() {
        return new LifeGraphBuildServiceImpl(entityRepository, aliasRepository, relationRepository,
                evidenceRepository, mentionRepository, promptManager, extractor, new ObjectMapper());
    }
}
