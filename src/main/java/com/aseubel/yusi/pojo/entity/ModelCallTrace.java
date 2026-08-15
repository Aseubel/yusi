package com.aseubel.yusi.pojo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@Entity
@DynamicUpdate
@Table(name = "model_call_trace", uniqueConstraints = {
        @UniqueConstraint(name = "uk_model_call_trace_request_attempt", columnNames = { "request_id", "attempt_id" })
}, indexes = {
        @Index(name = "idx_model_call_trace_created_scene", columnList = "created_at, scene"),
        @Index(name = "idx_model_call_trace_tier_created", columnList = "selected_tier, created_at"),
        @Index(name = "idx_model_call_trace_provider_created", columnList = "provider, created_at"),
        @Index(name = "idx_model_call_trace_status_created", columnList = "status, created_at"),
        @Index(name = "idx_model_call_trace_fallback_created", columnList = "fallback_used, created_at"),
        @Index(name = "idx_model_call_trace_prompt_version", columnList = "prompt_key, prompt_version, created_at"),
        @Index(name = "idx_model_call_trace_user_run_created", columnList = "user_id, run_id, created_at")
})
@NoArgsConstructor
@AllArgsConstructor
public class ModelCallTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "attempt_id", nullable = false, length = 64)
    private String attemptId;

    @Column(name = "run_id", length = 64)
    private String runId;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "scene", nullable = false, length = 64)
    private String scene;

    @Column(name = "prompt_key", length = 64)
    private String promptKey;

    @Column(name = "prompt_version", length = 64)
    private String promptVersion;

    @Column(name = "prompt_locale", length = 16)
    private String promptLocale;

    @Column(name = "policy_id", length = 128)
    private String policyId;

    @Column(name = "policy_version")
    private Long policyVersion;

    @Column(name = "route_reason", length = 1024)
    private String routeReason;

    @Column(name = "primary_tier", length = 64)
    private String primaryTier;

    @Column(name = "selected_tier", length = 64)
    private String selectedTier;

    @Column(name = "model_id", length = 128)
    private String modelId;

    @Column(name = "provider", length = 64)
    private String provider;

    @Column(name = "model_name", length = 256)
    private String modelName;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "cached_tokens")
    private Long cachedTokens;

    @Column(name = "cost", precision = 20, scale = 10)
    private BigDecimal cost;

    @Column(name = "price_version", length = 64)
    private String priceVersion;

    @Column(name = "usage_source", length = 32)
    private String usageSource;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "ttft_ms")
    private Long ttftMs;

    @Column(name = "retry_index", nullable = false)
    private Integer retryIndex;

    @Column(name = "fallback_used", nullable = false)
    private Boolean fallbackUsed;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "finish_reason", length = 64)
    private String finishReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (retryIndex == null) {
            retryIndex = 0;
        }
        if (fallbackUsed == null) {
            fallbackUsed = false;
        }
    }
}
