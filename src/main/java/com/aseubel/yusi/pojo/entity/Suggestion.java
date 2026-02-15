package com.aseubel.yusi.pojo.entity;

import com.aseubel.yusi.common.utils.UuidUtils;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

/**
 * 用户建议/反馈实体
 * @author Aseubel
 */
@Data
@Entity
@Builder
@Table(name = "suggestion")
@DynamicInsert
@DynamicUpdate
@AllArgsConstructor
@NoArgsConstructor
public class Suggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "suggestion_id", unique = true)
    private String suggestionId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "status")
    private String status = "PENDING";

    @Column(name = "reply", columnDefinition = "TEXT")
    private String reply;

    @Column(name = "replied_by")
    private String repliedBy;

    @Column(name = "replied_at")
    private LocalDateTime repliedAt;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        if (this.suggestionId == null) {
            this.suggestionId = UuidUtils.genUuidSimple();
        }
        if (this.createTime == null) {
            this.createTime = LocalDateTime.now();
        }
        if (this.updateTime == null) {
            this.updateTime = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}
