package com.aseubel.yusi.service.memory;

import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.service.cognition.CognitiveConflictDetector;
import com.aseubel.yusi.service.memory.impl.MidMemoryUpdateServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MidMemoryUpdateServiceTest {

    @Mock
    private MidTermMemoryRepository memoryRepository;

    @Mock
    private CognitiveConflictDetector conflictDetector;

    @Mock
    private ThreadPoolTaskExecutor threadPoolExecutor;

    @Mock
    private MidTermMemoryVectorService vectorService;

    @Test
    void removesOnlyTheCurrentUsersMemoriesAndTheirVectors() {
        MidTermMemory first = memory(1L, "user-1", "old-1");
        MidTermMemory second = memory(2L, "user-1", "old-2");
        when(memoryRepository.findByUserIdAndSourceTypeAndSourceId("user-1", "DIARY", "diary-1"))
                .thenReturn(List.of(first, second));

        service().removeBySource("user-1", "DIARY", "diary-1");

        verify(memoryRepository).findByUserIdAndSourceTypeAndSourceId("user-1", "DIARY", "diary-1");
        verify(memoryRepository).delete(first);
        verify(memoryRepository).delete(second);
        verify(vectorService).delete(1L);
        verify(vectorService).delete(2L);
    }

    private MidMemoryUpdateServiceImpl service() {
        return new MidMemoryUpdateServiceImpl(memoryRepository, conflictDetector, threadPoolExecutor,
                vectorService);
    }

    private MidTermMemory memory(Long id, String userId, String summary) {
        return MidTermMemory.builder()
                .id(id)
                .userId(userId)
                .sourceType("DIARY")
                .sourceId("diary-1")
                .summary(summary)
                .importance(0.7)
                .build();
    }
}
