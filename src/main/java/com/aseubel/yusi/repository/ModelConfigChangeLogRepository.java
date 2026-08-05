package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.ModelConfigChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelConfigChangeLogRepository extends JpaRepository<ModelConfigChangeLog, Long> {
}
