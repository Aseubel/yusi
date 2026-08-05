package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.ModelRuntimeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModelRuntimeConfigRepository extends JpaRepository<ModelRuntimeConfig, Long> {

    Optional<ModelRuntimeConfig> findByConfigKey(String configKey);
}
