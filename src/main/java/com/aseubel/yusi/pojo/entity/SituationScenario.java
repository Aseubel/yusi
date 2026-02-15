package com.aseubel.yusi.pojo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "situation_scenario")
@AllArgsConstructor
@NoArgsConstructor
public class SituationScenario {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_MANUAL_REJECTED = 1;
    public static final int STATUS_AI_REJECTED = 2;
    public static final int STATUS_AI_APPROVED = 3;
    public static final int STATUS_MANUAL_APPROVED = 4;
    public static final int STATUS_DELETED = -1;

    public static String getStatusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case STATUS_PENDING -> "待审核";
            case STATUS_MANUAL_REJECTED -> "已拒绝";
            case STATUS_AI_REJECTED -> "AI审核拒绝";
            case STATUS_AI_APPROVED -> "AI审核通过";
            case STATUS_MANUAL_APPROVED -> "已通过";
            case STATUS_DELETED -> "已删除";
            default -> "未知";
        };
    }

    @Id
    @Column(length = 32)
    private String id;

    @Column(length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "submitter_id")
    private String submitterId;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer status = 0;

    public String getContentString() {
        return "标题：" + title + "\n" + "描述：" + description;
    }
}