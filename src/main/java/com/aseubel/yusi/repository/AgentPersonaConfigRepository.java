package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.AgentPersonaConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentPersonaConfigRepository extends JpaRepository<AgentPersonaConfig, Long> {

    Optional<AgentPersonaConfig> findByUserId(String userId);
}
