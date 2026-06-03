package com.aseubel.yusi.pojo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mid_term_memory")
public class MidTermMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "importance", nullable = false)
    private Double importance;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 记忆有效期截止时间。
     * 过期后自动降低匹配和对话上下文中的权重，null 表示永不过期。
     */
    // TODO Phase 5 (F11.5): 添加定时任务定期清理 expired (validUntil < now) 的记忆，实施遗忘机制
    @Column(name = "valid_until")
    private LocalDateTime validUntil;
}
