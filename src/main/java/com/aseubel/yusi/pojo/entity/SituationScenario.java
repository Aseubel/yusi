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

    // 0-待审核/1-人工拒绝/2-AI 审核拒绝/3-AI 审核通过/4-人工通过
    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer status = 0;

    public String getContentString() {
        return "标题：" + title + "\n" + "描述：" + description;
    }
}