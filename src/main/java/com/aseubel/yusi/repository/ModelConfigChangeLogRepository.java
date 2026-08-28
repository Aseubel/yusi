package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.ModelConfigChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelConfigChangeLogRepository extends JpaRepository<ModelConfigChangeLog, Long> {

    /** 最近 500 条成功变更记录，用于历史版本列表与快照读取。 */
    List<ModelConfigChangeLog> findTop500BySuccessTrueOrderByCreatedAtDesc();
}
