package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromptRepository extends JpaRepository<PromptTemplate, Long> {
    Optional<PromptTemplate> findByNameAndActiveTrue(String name);
}
