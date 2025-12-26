package com.aseubel.yusi.pojo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

/**
 * 匹配用户间的聊天消息
 */
@Data
@Entity
@Builder
@Table(name = "soul_message")
@DynamicInsert
@DynamicUpdate
@AllArgsConstructor
@NoArgsConstructor
public class SoulMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id")
    private Long matchId;

    @Column(name = "sender_id")
    private String senderId;

    @Column(name = "receiver_id")
    private String receiverId;

    @Column(name = "content", length = 1000)
    private String content;

    @Column(name = "is_read")
    private Boolean isRead;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
