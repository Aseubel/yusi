package com.aseubel.yusi.pojo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 房间临时聊天消息
 * 与房间生命周期绑定，房间结束后消息保留但不可再发送
 */
@Data
@Builder(toBuilder = true)
@Entity
@Table(name = "room_message", indexes = {
        @Index(name = "idx_room_code", columnList = "roomCode"),
        @Index(name = "idx_created_at", columnList = "createdAt")
})
@NoArgsConstructor
@AllArgsConstructor
public class RoomMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_code", length = 32, nullable = false)
    private String roomCode;

    @Column(name = "sender_id", length = 64, nullable = false)
    private String senderId;

    @Column(name = "sender_name", length = 64)
    private String senderName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
