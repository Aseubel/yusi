package com.aseubel.yusi.pojo.entity;

import com.aseubel.yusi.pojo.contant.CardType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@Entity
@Table(name = "soul_card")
@NoArgsConstructor
@AllArgsConstructor
public class SoulCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String content;

    // Original source ID (Diary ID or Situation Room Code)
    @Column(name = "origin_id")
    private String originId;

    // Original author (Hidden from frontend response usually)
    @Column(name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    private CardType type;

    // AI analyzed primary emotion/theme for filtering
    private String emotion;

    @Column(name = "resonance_count")
    @Builder.Default
    private Integer resonanceCount = 0;

    @Column(name = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
