package com.aseubel.yusi.service.task;

import com.aseubel.yusi.pojo.constant.TaskExecutionSourceType;
import com.aseubel.yusi.pojo.constant.TaskExecutionStatus;
import com.aseubel.yusi.pojo.constant.TaskExecutionType;
import com.aseubel.yusi.pojo.constant.TaskFailureCategory;
import com.aseubel.yusi.pojo.entity.TaskExecution;
import com.aseubel.yusi.repository.TaskExecutionRepository;
import com.aseubel.yusi.service.security.SecurityAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskExecutionServiceTest {

    @Mock
    private TaskExecutionRepository repository;

    @Mock
    private SecurityAuditService securityAuditService;

    @Test
    void duplicateIdempotencyKeyReturnsExistingExecutionWithoutSecondInsert() {
        TaskExecution existing = TaskExecution.builder()
                .taskId("task-existing")
                .taskType(TaskExecutionType.DIARY)
                .sourceType(TaskExecutionSourceType.DIARY.code())
                .sourceId("diary-1")
                .idempotencyKey("embedding:diary-1:event-1")
                .status(TaskExecutionStatus.PENDING)
                .build();
        when(repository.findByIdempotencyKey("embedding:diary-1:event-1"))
                .thenReturn(Optional.of(existing));

        TaskExecution result = service().createOrGet(command("embedding:diary-1:event-1"));

        assertEquals("task-existing", result.getTaskId());
        verify(repository, never()).save(any(TaskExecution.class));
    }

    @Test
    void claimOnlySucceedsWhenRepositoryAtomicallyClaimsTheTask() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 14, 12, 0);
        TaskExecution claimed = TaskExecution.builder()
                .taskId("task-1")
                .taskType(TaskExecutionType.LIFE_GRAPH)
                .status(TaskExecutionStatus.RUNNING)
                .build();
        when(repository.claim("task-1", "life-graph-worker", now)).thenReturn(1, 0);
        when(repository.findByTaskId("task-1")).thenReturn(Optional.of(claimed));

        assertTrue(service().claim("task-1", "life-graph-worker", now).isPresent());
        assertTrue(service().claim("task-1", "life-graph-worker", now).isEmpty());
    }

    @Test
    void retryExhaustionStoresFailureCategoryAndCheckpoint() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 14, 12, 0);
        TaskExecution execution = TaskExecution.builder()
                .taskId("task-2")
                .taskType(TaskExecutionType.LIFE_GRAPH)
                .status(TaskExecutionStatus.RUNNING)
                .retryCount(4)
                .maxRetries(5)
                .build();
        when(repository.findByTaskId("task-2")).thenReturn(Optional.of(execution));
        when(repository.save(any(TaskExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskExecution result = service().retry("task-2", TaskFailureCategory.DEPENDENCY,
                "{\"cursor\":12}", now);

        assertEquals(TaskExecutionStatus.FAILED, result.getStatus());
        assertEquals(5, result.getRetryCount());
        assertEquals(TaskFailureCategory.DEPENDENCY, result.getFailureCategory());
        assertEquals("{\"cursor\":12}", result.getCheckpointJson());
        assertEquals(now, result.getCompletedAt());
        verify(securityAuditService).record(any());
    }

    @Test
    void sourceEventCanBeRecordedAsCompleted() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 14, 12, 0);
        when(repository.findByIdempotencyKey("diary:event-1")).thenReturn(Optional.empty());
        when(repository.save(any(TaskExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskExecution result = service().recordCompleted(command("diary:event-1"), now);

        assertEquals(TaskExecutionType.DIARY, result.getTaskType());
        assertEquals(TaskExecutionStatus.SUCCEEDED, result.getStatus());
        assertEquals(now, result.getCompletedAt());
    }

    @Test
    void lateRetryCannotMoveACompletedExecutionBackToFailure() {
        TaskExecution execution = TaskExecution.builder()
                .taskId("task-completed")
                .taskType(TaskExecutionType.EMBEDDING)
                .status(TaskExecutionStatus.SUCCEEDED)
                .retryCount(1)
                .maxRetries(5)
                .build();
        when(repository.findByTaskId("task-completed")).thenReturn(Optional.of(execution));

        TaskExecution result = service().retry("task-completed", TaskFailureCategory.DEPENDENCY,
                null, LocalDateTime.now());

        assertEquals(TaskExecutionStatus.SUCCEEDED, result.getStatus());
        assertEquals(1, result.getRetryCount());
        verify(repository, never()).save(any(TaskExecution.class));
    }

    @Test
    void rejectsOversizedCheckpointBeforePersistence() {
        TaskExecutionService service = service();

        assertThrows(IllegalArgumentException.class,
                () -> service.createOrGet(TaskExecutionCommand.builder()
                        .taskType(TaskExecutionType.DIARY)
                        .sourceType(TaskExecutionSourceType.DIARY.code())
                        .sourceId("diary-1")
                        .idempotencyKey("diary:event-2")
                        .checkpointJson("x".repeat(2049))
                        .build()));

        verify(repository, never()).save(any(TaskExecution.class));
    }

    private TaskExecutionService service() {
        return new TaskExecutionService(repository, securityAuditService);
    }

    private TaskExecutionCommand command(String idempotencyKey) {
        return TaskExecutionCommand.builder()
                .taskType(TaskExecutionType.DIARY)
                .ownerUserId("user-1")
                .sourceType(TaskExecutionSourceType.DIARY.code())
                .sourceId("diary-1")
                .triggerEventId("event-1")
                .idempotencyKey(idempotencyKey)
                .build();
    }
}
