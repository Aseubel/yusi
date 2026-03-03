package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.ChatMemoryMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMemoryMessageRepository extends JpaRepository<ChatMemoryMessage, Long> {

    List<ChatMemoryMessage> findByMemoryIdOrderByCreatedAtAsc(String memoryId);

    // Load latest N messages (need to reverse list after loading)
    List<ChatMemoryMessage> findByMemoryIdOrderByCreatedAtDesc(String memoryId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM ChatMemoryMessage m WHERE m.memoryId = :memoryId")
    void deleteByMemoryId(String memoryId);

    /**
     * 查找指定用户未总结的消息数量
     */
    @Query("SELECT COUNT(m) FROM ChatMemoryMessage m WHERE m.memoryId = :memoryId AND m.isSummarized = false")
    long countUnsummarizedMessages(String memoryId);

    /**
     * 查找指定用户未总结的消息（按创建时间升序）
     */
    List<ChatMemoryMessage> findByMemoryIdAndIsSummarizedOrderByCreatedAtAsc(String memoryId, Boolean isSummarized,
            Pageable pageable);

    /**
     * 查找最后一条消息的创建时间
     */
    @Query("SELECT MAX(m.createdAt) FROM ChatMemoryMessage m WHERE m.memoryId = :memoryId")
    LocalDateTime findLastMessageTime(String memoryId);

    long countByMemoryId(String memoryId);

    long countByMemoryIdAndRole(String memoryId, String role);

    /**
     * 查询所有有未总结消息的 memoryId（去重）
     * 供定时任务精准过滤，替代全用户扫描
     */
    @Query("SELECT DISTINCT m.memoryId FROM ChatMemoryMessage m WHERE m.isSummarized = false")
    List<String> findMemoryIdsWithUnsummarizedMessages();
}
