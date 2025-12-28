package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.InterfaceDailyUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Repository
public interface InterfaceDailyUsageRepository extends JpaRepository<InterfaceDailyUsage, Long> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO interface_daily_usage (user_id, ip, interface_name, usage_date, request_count, created_at, updated_at)
            VALUES (?1, ?2, ?3, ?4, ?5, NOW(), NOW())
            ON DUPLICATE KEY UPDATE request_count = ?5, updated_at = NOW()
            """, nativeQuery = true)
    void upsertUsage(String userId, String ip, String interfaceName, LocalDate usageDate, Long requestCount);
}
