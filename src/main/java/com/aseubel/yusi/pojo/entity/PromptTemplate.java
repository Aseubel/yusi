package com.aseubel.yusi.pojo.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@Table(name = "prompt_template", uniqueConstraints = {
        @UniqueConstraint(name = "uk_prompt_template_name_locale", columnNames = { "name", "locale" })
})
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor
@AllArgsConstructor
public class PromptTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String template;

    @Column(nullable = false, length = 64)
    private String version;

    @Column(nullable = false)
    private Boolean active;

    @Column(nullable = false, length = 64)
    private String scope;

    @Column(nullable = false, length = 16)
    private String locale;

    @Column(length = 500)
    private String description;

    @Column(length = 255)
    private String tags;

    @Column(nullable = false)
    @JsonProperty("isDefault")
    private Boolean isDefault;

    @Column(nullable = false)
    private Integer priority;

    @Column(length = 64)
    private String updatedBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (version == null) {
            version = "v1";
        }
        if (scope == null) {
            scope = "global";
        }
        if (locale == null) {
            locale = "zh-CN";
        }
        if (active == null) {
            active = true;
        }
        if (isDefault == null) {
            isDefault = false;
        }
        if (priority == null) {
            priority = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (version == null) {
            version = "v1";
        }
        if (scope == null) {
            scope = "global";
        }
        if (locale == null) {
            locale = "zh-CN";
        }
        if (active == null) {
            active = true;
        }
        if (isDefault == null) {
            isDefault = false;
        }
        if (priority == null) {
            priority = 0;
        }
    }
}
