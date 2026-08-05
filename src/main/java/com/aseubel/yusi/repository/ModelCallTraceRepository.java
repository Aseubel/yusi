package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.ModelCallTrace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelCallTraceRepository extends JpaRepository<ModelCallTrace, Long>,
        JpaSpecificationExecutor<ModelCallTrace> {
}
