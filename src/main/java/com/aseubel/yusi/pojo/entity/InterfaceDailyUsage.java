package com.aseubel.yusi.pojo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户接口每日调用统计
 */
@Data
@Entity
@Builder
@Table(name = "interface_daily_usage", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_ip_interface_date", columnNames = {"user_id", "ip", "interface_name", "usage_date"})
})
@DynamicInsert
@DynamicUpdate
@AllArgsConstructor
@NoArgsConstructor
public class InterfaceDailyUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "ip", nullable = false, length = 64)
    private String ip;

    @Column(name = "interface_name", nullable = false, length = 128)
    private String interfaceName;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "request_count", nullable = false)
    private Long requestCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (requestCount == null) requestCount = 0L;
        if (ip == null) ip = "";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
