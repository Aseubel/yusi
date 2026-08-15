package com.aseubel.yusi.service.task;

import cn.hutool.core.util.IdUtil;
import com.aseubel.yusi.pojo.constant.SecurityAuditAction;
import com.aseubel.yusi.pojo.constant.SecurityAuditActorType;
import com.aseubel.yusi.pojo.constant.SecurityAuditDetailKeys;
import com.aseubel.yusi.pojo.constant.SecurityAuditOutcome;
import com.aseubel.yusi.pojo.constant.SecurityAuditResourceType;
import com.aseubel.yusi.pojo.constant.TaskExecutionStatus;
import com.aseubel.yusi.pojo.constant.TaskFailureCategory;
import com.aseubel.yusi.pojo.entity.TaskExecution;
import com.aseubel.yusi.repository.TaskExecutionRepository;
import com.aseubel.yusi.service.security.SecurityAuditCommand;
import com.aseubel.yusi.service.security.SecurityAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Coordinates durable task state without storing task input or model output. */
@Service
public class TaskExecutionService {

    private static final int DEFAULT_MAX_RETRIES = 5;
    private static final int MAX_RETRIES = 20;
    private static final int MAX_CHECKPOINT_LENGTH = 2048;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 191;
    private static final long MAX_RETRY_DELAY_SECONDS = 3600L;
    private static final long STALE_TIMEOUT_MINUTES = 30L;

    private final TaskExecutionRepository repository;
    private final SecurityAuditService securityAuditService;

    public TaskExecutionService(TaskExecutionRepository repository) {
        this(repository, null);
    }

    @Autowired
    public TaskExecutionService(TaskExecutionRepository repository, SecurityAuditService securityAuditService) {
        this.repository = repository;
        this.securityAuditService = securityAuditService;
    }

    @Transactional
    public TaskExecution createOrGet(TaskExecutionCommand command) {
        validate(command);
        Optional<TaskExecution> existing = repository.findByIdempotencyKey(command.getIdempotencyKey());
        if (existing.isPresent()) {
            validateReplay(existing.get(), command);
            return existing.get();
        }

        LocalDateTime now = LocalDateTime.now();
        return repository.save(TaskExecution.builder()
                .taskId(IdUtil.fastSimpleUUID())
                .taskType(command.getTaskType())
                .ownerUserId(command.getOwnerUserId())
                .sourceType(command.getSourceType())
                .sourceId(command.getSourceId())
                .sourceVersion(command.getSourceVersion())
                .triggerEventId(command.getTriggerEventId())
                .runId(command.getRunId())
                .idempotencyKey(command.getIdempotencyKey())
                .status(TaskExecutionStatus.PENDING)
                .retryCount(0)
                .maxRetries(resolveMaxRetries(command.getMaxRetries()))
                .checkpointJson(command.getCheckpointJson())
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .version(0L)
                .build());
    }

    @Transactional(readOnly = true)
    public Optional<TaskExecution> findByTaskId(String taskId) {
        if (isBlank(taskId)) {
            return Optional.empty();
        }
        return repository.findByTaskId(taskId);
    }

    @Transactional
    public TaskExecution ensureRunId(String taskId, String runId) {
        TaskExecution execution = require(taskId);
        if (isBlank(execution.getRunId()) && !isBlank(runId)) {
            execution.setRunId(runId);
            execution.setUpdatedAt(LocalDateTime.now());
            return repository.save(execution);
        }
        return execution;
    }

    @Transactional
    public TaskExecution recordCompleted(TaskExecutionCommand command, LocalDateTime completedAt) {
        TaskExecution execution = createOrGet(command);
        if (isTerminal(execution.getStatus())) {
            return execution;
        }
        validateCheckpoint(command.getCheckpointJson());
        return complete(execution, command.getCheckpointJson(), completedAt);
    }

    @Transactional
    public Optional<TaskExecution> claim(String taskId, String workerId, LocalDateTime now) {
        if (isBlank(taskId) || isBlank(workerId) || now == null) {
            return Optional.empty();
        }
        if (repository.claim(taskId, workerId, now) == 0) {
            return Optional.empty();
        }
        return repository.findByTaskId(taskId);
    }

    @Transactional
    public TaskExecution succeed(String taskId, String checkpointJson, LocalDateTime completedAt) {
        validateCheckpoint(checkpointJson);
        TaskExecution execution = require(taskId);
        if (execution.getStatus() == TaskExecutionStatus.SUCCEEDED) {
            return execution;
        }
        if (execution.getStatus() == TaskExecutionStatus.FAILED
                || execution.getStatus() == TaskExecutionStatus.CANCELLED) {
            return execution;
        }
        return complete(execution, checkpointJson, completedAt);
    }

    private TaskExecution complete(TaskExecution execution, String checkpointJson,
            LocalDateTime completedAt) {
        execution.setStatus(TaskExecutionStatus.SUCCEEDED);
        execution.setCheckpointJson(checkpointJson);
        execution.setCompletedAt(completedAt == null ? LocalDateTime.now() : completedAt);
        execution.setNextAttemptAt(null);
        execution.setClaimedAt(null);
        execution.setClaimedBy(null);
        execution.setUpdatedAt(execution.getCompletedAt());
        return repository.save(execution);
    }

    @Transactional
    public TaskExecution retry(String taskId, TaskFailureCategory failureCategory,
            String checkpointJson, LocalDateTime now) {
        validateCheckpoint(checkpointJson);
        TaskExecution execution = require(taskId);
        if (isTerminal(execution.getStatus())) {
            return execution;
        }
        LocalDateTime effectiveNow = now == null ? LocalDateTime.now() : now;
        int retryCount = (execution.getRetryCount() == null ? 0 : execution.getRetryCount()) + 1;
        execution.setRetryCount(retryCount);
        execution.setFailureCategory(failureCategory == null ? TaskFailureCategory.UNKNOWN : failureCategory);
        execution.setCheckpointJson(checkpointJson);
        execution.setClaimedAt(null);
        execution.setClaimedBy(null);
        execution.setUpdatedAt(effectiveNow);
        if (retryCount >= resolveMaxRetries(execution.getMaxRetries())) {
            execution.setStatus(TaskExecutionStatus.FAILED);
            execution.setNextAttemptAt(null);
            execution.setCompletedAt(effectiveNow);
        } else {
            execution.setStatus(TaskExecutionStatus.RETRY_WAIT);
            execution.setNextAttemptAt(effectiveNow.plusSeconds(retryDelaySeconds(retryCount)));
        }
        TaskExecution saved = repository.save(execution);
        if (saved == null) {
            saved = execution;
        }
        if (saved.getStatus() == TaskExecutionStatus.FAILED) {
            recordFailure(saved, effectiveNow);
        }
        return saved;
    }

    @Transactional
    public TaskExecution fail(String taskId, TaskFailureCategory failureCategory,
            String checkpointJson, LocalDateTime failedAt) {
        validateCheckpoint(checkpointJson);
        TaskExecution execution = require(taskId);
        if (isTerminal(execution.getStatus())) {
            return execution;
        }
        LocalDateTime effectiveTime = failedAt == null ? LocalDateTime.now() : failedAt;
        execution.setStatus(TaskExecutionStatus.FAILED);
        execution.setFailureCategory(failureCategory == null ? TaskFailureCategory.UNKNOWN : failureCategory);
        execution.setCheckpointJson(checkpointJson);
        execution.setNextAttemptAt(null);
        execution.setClaimedAt(null);
        execution.setClaimedBy(null);
        execution.setCompletedAt(effectiveTime);
        execution.setUpdatedAt(effectiveTime);
        TaskExecution saved = repository.save(execution);
        if (saved == null) {
            saved = execution;
        }
        recordFailure(saved, effectiveTime);
        return saved;
    }

    @Transactional
    public int recoverStaleTasks(LocalDateTime now) {
        LocalDateTime effectiveNow = now == null ? LocalDateTime.now() : now;
        return repository.recoverStale(effectiveNow.minusMinutes(STALE_TIMEOUT_MINUTES), effectiveNow,
                TaskFailureCategory.TIMEOUT);
    }

    private TaskExecution require(String taskId) {
        if (isBlank(taskId)) {
            throw new IllegalArgumentException("Task execution ID is required");
        }
        return repository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task execution does not exist"));
    }

    private void validate(TaskExecutionCommand command) {
        if (command == null || command.getTaskType() == null) {
            throw new IllegalArgumentException("Task execution type is required");
        }
        if (isBlank(command.getSourceType()) || isBlank(command.getSourceId())) {
            throw new IllegalArgumentException("Task execution source is required");
        }
        if (isBlank(command.getIdempotencyKey())
                || command.getIdempotencyKey().length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException("Task execution idempotency key is invalid");
        }
        if (!isBlank(command.getOwnerUserId()) && command.getOwnerUserId().length() > 64) {
            throw new IllegalArgumentException("Task execution owner is invalid");
        }
        if (command.getMaxRetries() != null
                && (command.getMaxRetries() < 1 || command.getMaxRetries() > MAX_RETRIES)) {
            throw new IllegalArgumentException("Task execution retry limit is invalid");
        }
        validateCheckpoint(command.getCheckpointJson());
    }

    private void validateReplay(TaskExecution existing, TaskExecutionCommand command) {
        if (existing.getTaskType() != command.getTaskType()
                || !command.getSourceType().equals(existing.getSourceType())
                || !command.getSourceId().equals(existing.getSourceId())) {
            throw new IllegalArgumentException("Idempotency key belongs to another task execution");
        }
    }

    private int resolveMaxRetries(Integer maxRetries) {
        return maxRetries == null ? DEFAULT_MAX_RETRIES : maxRetries;
    }

    private long retryDelaySeconds(int retryCount) {
        long delay = 5L;
        for (int i = 1; i < retryCount; i++) {
            if (delay >= MAX_RETRY_DELAY_SECONDS / 5L) {
                return MAX_RETRY_DELAY_SECONDS;
            }
            delay *= 5L;
        }
        return Math.min(delay, MAX_RETRY_DELAY_SECONDS);
    }

    private void validateCheckpoint(String checkpointJson) {
        if (checkpointJson != null && checkpointJson.length() > MAX_CHECKPOINT_LENGTH) {
            throw new IllegalArgumentException("Task execution checkpoint is too large");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public boolean isTerminal(TaskExecutionStatus status) {
        return status == TaskExecutionStatus.SUCCEEDED
                || status == TaskExecutionStatus.FAILED
                || status == TaskExecutionStatus.CANCELLED;
    }

    private void recordFailure(TaskExecution execution, LocalDateTime occurredAt) {
        if (securityAuditService == null || execution == null) {
            return;
        }
        Map<String, String> details = new LinkedHashMap<>();
        putDetail(details, SecurityAuditDetailKeys.TASK_TYPE,
                execution.getTaskType() == null ? null : execution.getTaskType().code());
        putDetail(details, SecurityAuditDetailKeys.FAILURE_CATEGORY,
                execution.getFailureCategory() == null ? null : execution.getFailureCategory().name());
        putDetail(details, SecurityAuditDetailKeys.RETRY_COUNT,
                execution.getRetryCount() == null ? null : String.valueOf(execution.getRetryCount()));
        putDetail(details, SecurityAuditDetailKeys.SOURCE_TYPE, execution.getSourceType());
        securityAuditService.record(SecurityAuditCommand.builder()
                .action(SecurityAuditAction.TASK_FAILED)
                .actorType(SecurityAuditActorType.SYSTEM)
                .subjectUserId(execution.getOwnerUserId())
                .resourceType(SecurityAuditResourceType.TASK_EXECUTION)
                .resourceId(execution.getTaskId())
                .outcome(SecurityAuditOutcome.FAILURE)
                .details(details)
                .scopeUserIds(execution.getOwnerUserId() == null
                        ? java.util.Set.of() : java.util.Set.of(execution.getOwnerUserId()))
                .occurredAt(occurredAt)
                .build());
    }

    private void putDetail(Map<String, String> details, String key, String value) {
        if (value != null) {
            details.put(key, value);
        }
    }
}
