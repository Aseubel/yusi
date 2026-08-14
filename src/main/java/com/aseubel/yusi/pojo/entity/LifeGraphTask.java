package com.aseubel.yusi.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@ToString
@Table(name = "life_graph_task")
@DynamicInsert
@DynamicUpdate
@AllArgsConstructor
@NoArgsConstructor
public class LifeGraphTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "diary_id", nullable = false)
    private String diaryId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /**
     * 触发任务的日记变更事件 ID；同一任务重试时保持不变。
     */
    @Column(name = "trigger_event_id", length = 64)
    private String triggerEventId;

    /** Stable ID in the cross-domain task execution ledger. */
    @Column(name = "task_execution_id", length = 64)
    private String taskExecutionId;

    @Column(name = "task_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TaskType taskType;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "max_retries")
    private Integer maxRetries;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime updatedAt;

    @Column(name = "next_retry_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime nextRetryAt;

    public enum TaskType {
        UPSERT,
        DELETE
    }

    public enum TaskStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    public static LifeGraphTask createUpsertTask(String diaryId, String userId) {
        return createUpsertTask(diaryId, userId, null);
    }

    public static LifeGraphTask createUpsertTask(String diaryId, String userId, String triggerEventId) {
        return LifeGraphTask.builder()
                .diaryId(diaryId)
                .userId(userId)
                .triggerEventId(triggerEventId)
                .taskType(TaskType.UPSERT)
                .status(TaskStatus.PENDING)
                .retryCount(0)
                .maxRetries(5)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .nextRetryAt(LocalDateTime.now())
                .build();
    }

    public static LifeGraphTask createDeleteTask(String diaryId, String userId) {
        return createDeleteTask(diaryId, userId, null);
    }

    public static LifeGraphTask createDeleteTask(String diaryId, String userId, String triggerEventId) {
        return LifeGraphTask.builder()
                .diaryId(diaryId)
                .userId(userId)
                .triggerEventId(triggerEventId)
                .taskType(TaskType.DELETE)
                .status(TaskStatus.PENDING)
                .retryCount(0)
                .maxRetries(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .nextRetryAt(LocalDateTime.now())
                .build();
    }
}
