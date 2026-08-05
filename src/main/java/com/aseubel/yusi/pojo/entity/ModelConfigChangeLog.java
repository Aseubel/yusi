package com.aseubel.yusi.pojo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Builder
@Entity
@Table(name = "model_config_change_log")
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "change_id", nullable = false, unique = true, length = 64)
    private String changeId;

    @Column(name = "operator_id", length = 64)
    private String operatorId;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "group_name", length = 64)
    private String groupName;

    @Lob
    @Column(name = "before_json", columnDefinition = "JSON")
    private String beforeJson;

    @Lob
    @Column(name = "after_json", columnDefinition = "JSON")
    private String afterJson;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
