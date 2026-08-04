package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.AgentRunTrace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentRunTraceRepository extends JpaRepository<AgentRunTrace, Long> {

    Optional<AgentRunTrace> findByUserIdAndRunId(String userId, String runId);
}
