package com.aseubel.yusi.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

/**
 * Milvus Embedding 任务表实体
 * 用于可靠的异步批量处理日记向量化
 *
 * @author Aseubel
 * @date 2026/2/3
 */
@Data
@Entity
@Builder
@ToString
@Table(name = "embedding_task")
@DynamicInsert
@DynamicUpdate
@AllArgsConstructor
@NoArgsConstructor
public class EmbeddingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的日记 ID
     */
    @Column(name = "diary_id", nullable = false)
    private String diaryId;

    /**
     * 关联的用户 ID
     */
    @Column(name = "user_id", nullable = false)
    private String userId;

    /**
     * 任务类型: UPSERT(新增/修改) / DELETE(删除)
     */
    @Column(name = "task_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TaskType taskType;

    /**
     * 任务状态: PENDING(待处理) / PROCESSING(处理中) / COMPLETED(已完成) / FAILED(失败)
     */
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    /**
     * 重试次数
     */
    @Column(name = "retry_count")
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    @Column(name = "max_retries")
    private Integer maxRetries;

    /**
     * 错误信息（失败时记录）
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime updatedAt;

    /**
     * 下次重试时间（用于指数退避）
     */
    @Column(name = "next_retry_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime nextRetryAt;

    public enum TaskType {
        UPSERT, // 新增或修改
        DELETE // 删除
    }

    public enum TaskStatus {
        PENDING, // 待处理
        PROCESSING, // 处理中
        COMPLETED, // 已完成
        FAILED // 永久失败（超过最大重试次数）
    }

    /**
     * 创建新增/修改任务
     */
    public static EmbeddingTask createUpsertTask(String diaryId, String userId) {
        return EmbeddingTask.builder()
                .diaryId(diaryId)
                .userId(userId)
                .taskType(TaskType.UPSERT)
                .status(TaskStatus.PENDING)
                .retryCount(0)
                .maxRetries(5)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .nextRetryAt(LocalDateTime.now())
                .build();
    }

    /**
     * 创建删除任务
     */
    public static EmbeddingTask createDeleteTask(String diaryId, String userId) {
        return EmbeddingTask.builder()
                .diaryId(diaryId)
                .userId(userId)
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
