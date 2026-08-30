package com.aseubel.yusi.pojo.entity;

import jakarta.persistence.*;
import com.aseubel.yusi.common.constant.SourceType;
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

    /** 记忆来源，例如 CHAT_SUMMARY、DIARY 或 PLAZA。 */
    @Column(name = "source_type", nullable = false, length = 32)
    @Builder.Default
    private String sourceType = SourceType.UNKNOWN.code();

    /** 来源记录 ID，用于回到产生这条记忆的原始记录。 */
    @Column(name = "source_id", length = 128)
    private String sourceId;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "importance", nullable = false)
    private Double importance;

    /** AI 对这条摘要的置信度，范围约定为 0 到 1。 */
    @Column(name = "confidence", nullable = false)
    @Builder.Default
    private Double confidence = 0.5;

    /** 是否允许这条记忆进入匹配画像。 */
    @Column(name = "match_allowed", nullable = false)
    @Builder.Default
    private Boolean matchAllowed = false;

    /** 用户隐藏后，Agent 的对话、主动问候和检索都不再使用它。 */
    @Column(name = "hidden", nullable = false)
    @Builder.Default
    private Boolean hidden = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 记忆创建时的初始重要性，作为完全遗忘判定的门槛基准（低重要性记忆才可能衰减遗忘）。 */
    @Column(name = "initial_importance")
    private Double initialImportance;

    /** 最后一次被检索命中的时间，作为衰减时钟基准（被想起的记忆会巩固）。 */
    @Column(name = "last_reinforced_at")
    private LocalDateTime lastReinforcedAt;

    /** 完全遗忘时间（消费时懒判定后落库），null 表示仍可被检索和注入。 */
    @Column(name = "forgotten_at")
    private LocalDateTime forgottenAt;

    /**
     * 若被跨源融合到另一条记忆，指向幸存记忆的 ID（F11.4）。
     * null 表示未被合并。
     */
    @Column(name = "merged_into_id")
    private Long mergedIntoId;
}
