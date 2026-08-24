package com.aseubel.yusi.pojo.entity;

import com.aseubel.yusi.config.jpa.SituationConverters.StringSetConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** Singleton persisted policy for browser origins and client IP admission. */
@Getter
@Setter
@Entity
@Table(name = "web_access_policy")
@NoArgsConstructor
public class WebAccessPolicy {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(name = "development_mode_enabled", nullable = false)
    private boolean developmentModeEnabled;

    @Column(name = "development_mode_expires_at")
    private LocalDateTime developmentModeExpiresAt;

    @Lob
    @Column(name = "allowed_origins", nullable = false, columnDefinition = "LONGTEXT")
    @Convert(converter = StringSetConverter.class)
    private Set<String> allowedOrigins = new LinkedHashSet<>();

    @Lob
    @Column(name = "blocked_origins", nullable = false, columnDefinition = "LONGTEXT")
    @Convert(converter = StringSetConverter.class)
    private Set<String> blockedOrigins = new LinkedHashSet<>();

    @Lob
    @Column(name = "allowed_ip_rules", nullable = false, columnDefinition = "LONGTEXT")
    @Convert(converter = StringSetConverter.class)
    private Set<String> allowedIpRules = new LinkedHashSet<>();

    @Lob
    @Column(name = "blocked_ip_rules", nullable = false, columnDefinition = "LONGTEXT")
    @Convert(converter = StringSetConverter.class)
    private Set<String> blockedIpRules = new LinkedHashSet<>();

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
