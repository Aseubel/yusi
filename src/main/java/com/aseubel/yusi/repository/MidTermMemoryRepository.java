package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.MidTermMemory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MidTermMemoryRepository
        extends JpaRepository<MidTermMemory, Long>, JpaSpecificationExecutor<MidTermMemory> {

    List<MidTermMemory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<MidTermMemory> findByUserId(String userId);

}
