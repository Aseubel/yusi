package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.SecurityAuditEventScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SecurityAuditEventScopeRepository extends JpaRepository<SecurityAuditEventScope, Long> {

    @Modifying
    @Query("DELETE FROM SecurityAuditEventScope s WHERE s.auditEventId IN :auditEventIds")
    int deleteByAuditEventIdIn(@Param("auditEventIds") List<Long> auditEventIds);
}
