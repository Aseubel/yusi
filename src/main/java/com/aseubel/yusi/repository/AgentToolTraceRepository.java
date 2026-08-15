package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.AgentToolTrace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentToolTraceRepository extends JpaRepository<AgentToolTrace, Long> {

    Optional<AgentToolTrace> findByUserIdAndRunIdAndToolCallId(
            String userId, String runId, String toolCallId);

    List<AgentToolTrace> findByUserIdAndRunIdAndStatus(
            String userId, String runId, AgentToolTrace.Status status);
}
