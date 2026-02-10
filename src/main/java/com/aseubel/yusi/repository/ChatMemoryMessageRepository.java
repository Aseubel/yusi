package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.ChatMemoryMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMemoryMessageRepository extends JpaRepository<ChatMemoryMessage, Long> {

    List<ChatMemoryMessage> findByMemoryIdOrderByCreatedAtAsc(String memoryId);

    // Load latest N messages (need to reverse list after loading)
    List<ChatMemoryMessage> findByMemoryIdOrderByCreatedAtDesc(String memoryId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM ChatMemoryMessage m WHERE m.memoryId = :memoryId")
    void deleteByMemoryId(String memoryId);
}
