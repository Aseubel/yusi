package com.aseubel.yusi.pojo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 广场共鸣信号。
 * 用户在广场看到他人内容后，可匿名发送轻量共鸣信号。
 * 与 SoulResonance（对帖子的共鸣）不同，这是用户间的一对一轻量连接。
 *
 * @author Aseubel
 * @date 2026/06/03
 */
@Data
@Entity
@Builder
@Table(name = "resonance_signal")
@AllArgsConstructor
@NoArgsConstructor
public class ResonanceSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 发送信号的用户ID */
    @Column(name = "from_user_id", nullable = false, length = 64)
    private String fromUserId;

    /** 接收信号的用户ID */
    @Column(name = "to_user_id", nullable = false, length = 64)
    private String toUserId;

    /** 触发共鸣的广场帖子ID（可选） */
    @Column(name = "card_id")
    private Long cardId;

    /** 附言（匿名，不超过100字） */
    @Column(name = "message", length = 200)
    private String message;

    /** 接收方是否已读 */
    @Column(name = "is_read")
    @Builder.Default
    private Boolean isRead = false;

    /** 是否已转化为相互共鸣（双方都发送了信号） */
    @Column(name = "is_mutual")
    @Builder.Default
    private Boolean isMutual = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
