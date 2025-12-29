package com.aseubel.yusi.pojo.entity;

import com.aseubel.yusi.pojo.contant.ResonanceType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@Entity
@Table(name = "soul_resonance")
@NoArgsConstructor
@AllArgsConstructor
public class SoulResonance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    private ResonanceType type;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
