package com.aseubel.yusi.pojo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 用户画像/偏好表
 * 用于存储用户的高保真、强规则信息，如称呼、所在地、兴趣爱好、相处模式等。
 *
 * @author Aseubel
 * @date 2026/02/10
 */
@Data
@Entity
@Builder
@Table(name = "user_persona")
@DynamicInsert
@DynamicUpdate
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserPersona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户ID (唯一索引)
     */
    @Column(name = "user_id", unique = true, nullable = false, length = 64)
    private String userId;

    /**
     * 称呼/姓名 (User wants to be called)
     */
    @Column(name = "preferred_name", length = 50)
    private String preferredName;

    /**
     * 所在地
     */
    @Column(name = "location", length = 100)
    private String location;

    /**
     * 兴趣/话题偏好 (e.g., "摄影, 旅行, 哲学")
     */
    @Column(name = "interests", length = 500)
    private String interests;

    /**
     * 语气要求 (e.g., "温柔", "傲娇", "倾听者")
     */
    @Column(name = "tone", length = 200)
    private String tone;

    /**
     * 自定义指令/相处模式 (e.g., "不用安慰我，静静陪着就好", "每天早上问候我")
     */
    @Column(name = "custom_instructions", columnDefinition = "TEXT")
    private String customInstructions;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
