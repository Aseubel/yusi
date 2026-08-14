package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.SecurityAuditEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, Long> {

    @Query("SELECT e FROM SecurityAuditEvent e "
            + "WHERE EXISTS (SELECT s.id FROM SecurityAuditEventScope s "
            + "WHERE s.auditEventId = e.id AND s.userId = :userId) "
            + "ORDER BY e.occurredAt DESC, e.id DESC")
    List<SecurityAuditEvent> findAccessibleToUser(@Param("userId") String userId, Pageable pageable);

    List<SecurityAuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    @Query("SELECT e.id FROM SecurityAuditEvent e WHERE e.occurredAt < :before")
    List<Long> findIdsByOccurredAtBefore(@Param("before") LocalDateTime before);

    @Modifying
    @Query("DELETE FROM SecurityAuditEvent e WHERE e.id IN :ids")
    int deleteByIdIn(@Param("ids") List<Long> ids);
}
