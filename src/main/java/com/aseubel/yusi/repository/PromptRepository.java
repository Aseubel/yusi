package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.PromptTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromptRepository extends JpaRepository<PromptTemplate, Long> {
        Optional<PromptTemplate> findTopByNameAndLocaleAndActiveTrueOrderByIsDefaultDescPriorityDescUpdatedAtDesc(
                        String name, String locale);

        List<PromptTemplate> findByNameAndLocaleAndScope(String name, String locale, String scope);

        Optional<PromptTemplate> findByNameAndLocale(String name, String locale);

        @Query("""
                        SELECT p FROM PromptTemplate p
                        WHERE (:name IS NULL OR p.name LIKE %:name%)
                          AND (:scope IS NULL OR p.scope = :scope)
                          AND (:locale IS NULL OR p.locale = :locale)
                          AND (:active IS NULL OR p.active = :active)
                        """)
        Page<PromptTemplate> searchPrompts(@Param("name") String name,
                        @Param("scope") String scope,
                        @Param("locale") String locale,
                        @Param("active") Boolean active,
                        Pageable pageable);
}
