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
 * @author Aseubel
 * @date 2025/12/21
 */
@Data
@Entity
@Builder
@Table(name = "soul_match")
@DynamicInsert
@DynamicUpdate
@AllArgsConstructor
@NoArgsConstructor
public class SoulMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_a_id")
    private String userAId;

    @Column(name = "user_b_id")
    private String userBId;

    @Column(name = "letter_a_to_b", length = 2000)
    private String letterAtoB;

    @Column(name = "letter_b_to_a", length = 2000)
    private String letterBtoA;

    // 0: Pending, 1: Interested, 2: Skipped
    @Column(name = "status_a")
    private Integer statusA;

    @Column(name = "status_b")
    private Integer statusB;

    @Column(name = "is_matched")
    private Boolean isMatched; // True if both Interested

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
