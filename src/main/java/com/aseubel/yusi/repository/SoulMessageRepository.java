package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.SoulMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface SoulMessageRepository extends JpaRepository<SoulMessage, Long> {
    List<SoulMessage> findByMatchIdOrderByCreateTimeAsc(Long matchId);
    
    // 查找未读消息
    long countByReceiverIdAndIsReadFalse(String receiverId);

    @Modifying
    @Transactional
    @Query("UPDATE SoulMessage m SET m.isRead = true WHERE m.matchId = ?1 AND m.receiverId = ?2 AND m.isRead = false")
    void markAsRead(Long matchId, String receiverId);
}
