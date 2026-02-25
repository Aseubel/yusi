package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.DeveloperConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeveloperConfigRepository extends JpaRepository<DeveloperConfig, Long> {
    Optional<DeveloperConfig> findByUserId(String userId);

    Optional<DeveloperConfig> findByApiKey(String apiKey);
}
