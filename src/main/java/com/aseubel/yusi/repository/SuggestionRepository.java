package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.Suggestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    Suggestion findBySuggestionId(String suggestionId);

    Page<Suggestion> findByStatus(String status, Pageable pageable);

    Page<Suggestion> findByUserId(String userId, Pageable pageable);

    List<Suggestion> findByStatus(String status);

    long countByStatus(String status);
}
