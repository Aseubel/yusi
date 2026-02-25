package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.Diary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Interface for complex diary queries needed by gRPC extensions.
 * This separates MCP/gRPC specific DB queries from the core DiaryRepository.
 */
@Repository
public interface DiaryExtensionRepository extends JpaRepository<Diary, Long>, JpaSpecificationExecutor<Diary> {
}
