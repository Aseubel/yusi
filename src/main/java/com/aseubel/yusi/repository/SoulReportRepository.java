package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.SoulReport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SoulReportRepository extends JpaRepository<SoulReport, Long> {

    /** 查询用户最近的报告（按创建时间倒序） */
    List<SoulReport> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /** 查询用户最新的某类报告 */
    Optional<SoulReport> findTopByUserIdAndReportTypeOrderByCreatedAtDesc(String userId, String reportType);

    /** 检查某周期是否已生成报告（去重） */
    boolean existsByUserIdAndReportTypeAndPeriodStart(String userId, String reportType, LocalDate periodStart);
}
