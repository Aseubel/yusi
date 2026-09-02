package com.aseubel.yusi.service.memory;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.pojo.dto.memory.UpdateMemoryRequest;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.service.match.MatchProfileAssembler;
import com.aseubel.yusi.service.security.SecurityAuditService;
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

    @Mock
    private SecurityAuditService securityAuditService;

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
    void listReportsLowValueDecayedMemoryAsForgotten() {
        // 半衰期遗忘机制后：低初始重要性 + 衰减后低于阈值的记忆按 FORGOTTEN 展示
        MidTermMemory memory = memory(8L, "user-1");
        // 创建时初始重要性与当前重要性一致（未被强化过），30 天衰减后 0.2 × 0.5^(30/14) ≈ 0.045 < 0.1
        memory.setInitialImportance(0.2);
        memory.setImportance(0.2);
        memory.setCreatedAt(LocalDateTime.now().minusDays(30));
        memory.setUpdatedAt(memory.getCreatedAt());
        when(memoryRepository.findByUserIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(java.util.List.of(memory));

        var result = service().list("user-1", 50);

        assertEquals("FORGOTTEN", result.getMemories().get(0).getLifecycleStatus());
        assertEquals(1L, result.getForgottenCount());
        assertEquals(0L, result.getActiveCount());
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
        verify(securityAuditService).record(any());
    }

    private MidTermMemoryLifecycleService service() {
        // 使用真实的半衰期衰减服务（默认配置）驱动懒遗忘展示
        com.aseubel.yusi.config.MemoryConfigProperties properties =
                new com.aseubel.yusi.config.MemoryConfigProperties();
        MemoryDecayService decayService = new MemoryDecayService(properties, memoryRepository);
        return new MidTermMemoryLifecycleService(memoryRepository, vectorService, matchProfileAssembler,
                diaryRepository, decayService, securityAuditService);
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
