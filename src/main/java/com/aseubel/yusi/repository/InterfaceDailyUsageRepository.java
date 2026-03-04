package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.InterfaceDailyUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

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

    /**
     * 批量插入或更新接口使用记录
     * 使用 ON DUPLICATE KEY UPDATE 实现批量 upsert
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO interface_daily_usage (user_id, ip, interface_name, usage_date, request_count, created_at, updated_at)
            VALUES 
            (:#{#records.[0].userId}, :#{#records.[0].ip}, :#{#records.[0].interfaceName}, :#{#records.[0].usageDate}, :#{#records.[0].requestCount}, NOW(), NOW())
            <#list records[1..] as record>
            , (:#{#record.userId}, :#{#record.ip}, :#{#record.interfaceName}, :#{#record.usageDate}, :#{#record.requestCount}, NOW(), NOW())
            </#list>
            ON DUPLICATE KEY UPDATE 
            request_count = VALUES(request_count),
            updated_at = NOW()
            """, nativeQuery = true)
    void batchUpsertUsage(List<InterfaceDailyUsage> records);

    /**
     * 批量插入或更新接口使用记录（使用原生 SQL）
     * 更高效的实现方式，避免 JPA 的批量操作性能问题
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO interface_daily_usage (user_id, ip, interface_name, usage_date, request_count, created_at, updated_at)
            VALUES 
            (:#{#records.![userId]}, :#{#records.![ip]}, :#{#records.![interfaceName]}, :#{#records.![usageDate]}, :#{#records.![requestCount]}, NOW(), NOW())
            ON DUPLICATE KEY UPDATE 
            request_count = VALUES(request_count),
            updated_at = NOW()
            """, nativeQuery = true)
    void batchUpsertUsageNative(List<InterfaceDailyUsage> records);
}
