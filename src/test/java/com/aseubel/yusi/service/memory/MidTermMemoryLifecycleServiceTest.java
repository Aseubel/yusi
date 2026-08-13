package com.aseubel.yusi.service.memory;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.pojo.dto.memory.UpdateMemoryRequest;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.service.match.MatchProfileAssembler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MidTermMemoryLifecycleServiceTest {

    @Mock
    private MidTermMemoryRepository memoryRepository;

    @Mock
    private MidTermMemoryVectorService vectorService;

    @Mock
    private MatchProfileAssembler matchProfileAssembler;

    @Mock
    private DiaryRepository diaryRepository;

    @Test
    void listResolvesDiarySourceTitleForTheCurrentUser() {
        MidTermMemory memory = memory(10L, "user-1");
        memory.setSourceType("DIARY");
        memory.setSourceId("diary-10");
        when(memoryRepository.findByUserIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(java.util.List.of(memory));
        when(diaryRepository.findByDiaryIdAndUserId("diary-10", "user-1"))
                .thenReturn(Diary.builder().diaryId("diary-10").userId("user-1").title("一次重要的旅行").build());

        var result = service().list("user-1", 50);

        assertEquals("一次重要的旅行", result.getMemories().get(0).getSourceTitle());
        verify(diaryRepository).findByDiaryIdAndUserId("diary-10", "user-1");
    }

    @Test
    void updateOnlyOperatesOnTheCurrentUsersMemory() {
        when(memoryRepository.findByIdAndUserId(7L, "user-1")).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service().delete("user-1", 7L));
        assertThrows(BusinessException.class, () -> service().update("user-1", 7L, new UpdateMemoryRequest()));

        verify(memoryRepository, never()).save(any());
        verify(memoryRepository, never()).delete(any(MidTermMemory.class));
        verifyNoInteractions(vectorService, matchProfileAssembler);
    }

    @Test
    void updateChangesSummaryVisibilityMatchScopeAndRefreshesProfile() {
        MidTermMemory memory = memory(7L, "user-1");
        when(memoryRepository.findByIdAndUserId(7L, "user-1")).thenReturn(Optional.of(memory));
        when(memoryRepository.save(any(MidTermMemory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateMemoryRequest request = new UpdateMemoryRequest();
        request.setSummary("修正后的记忆摘要");
        request.setConfidence(0.9);
        request.setMatchAllowed(true);
        request.setHidden(true);

        var result = service().update("user-1", 7L, request);

        assertEquals("修正后的记忆摘要", result.getSummary());
        assertEquals(0.9, result.getConfidence());
        assertTrue(result.getMatchAllowed());
        assertTrue(result.getHidden());
        assertEquals("HIDDEN", result.getLifecycleStatus());
        assertNotNull(result.getUpdatedAt());
        verify(vectorService).upsert(memory);
        verify(matchProfileAssembler).refreshProfile("user-1");
    }

    @Test
    void changingValidityAlsoRefreshesProfile() {
        MidTermMemory memory = memory(8L, "user-1");
        when(memoryRepository.findByIdAndUserId(8L, "user-1")).thenReturn(Optional.of(memory));
        when(memoryRepository.save(any(MidTermMemory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateMemoryRequest request = new UpdateMemoryRequest();
        request.setValidUntil(LocalDateTime.now().minusMinutes(1));

        var result = service().update("user-1", 8L, request);

        assertEquals(request.getValidUntil(), result.getValidUntil());
        assertEquals("EXPIRED", result.getLifecycleStatus());
        verify(matchProfileAssembler).refreshProfile("user-1");
        verify(vectorService, never()).upsert(any());
    }

    @Test
    void deleteRemovesDatabaseRowEvenWhenVectorCleanupFails() {
        MidTermMemory memory = memory(9L, "user-1");
        when(memoryRepository.findByIdAndUserId(9L, "user-1")).thenReturn(Optional.of(memory));
        doThrow(new RuntimeException("milvus unavailable")).when(vectorService).delete(9L);

        service().delete("user-1", 9L);

        verify(memoryRepository).delete(memory);
        verify(vectorService).delete(9L);
        verify(matchProfileAssembler).refreshProfile("user-1");
    }

    private MidTermMemoryLifecycleService service() {
        return new MidTermMemoryLifecycleService(memoryRepository, vectorService, matchProfileAssembler,
                diaryRepository);
    }

    private MidTermMemory memory(Long id, String userId) {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        return MidTermMemory.builder()
                .id(id)
                .userId(userId)
                .summary("原始记忆摘要")
                .importance(0.7)
                .confidence(0.6)
                .matchAllowed(false)
                .hidden(false)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }
}
