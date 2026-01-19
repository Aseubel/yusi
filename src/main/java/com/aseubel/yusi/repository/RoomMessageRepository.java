package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.RoomMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RoomMessageRepository extends JpaRepository<RoomMessage, Long> {

    /**
     * 获取房间的所有消息，按时间升序
     */
    List<RoomMessage> findByRoomCodeOrderByCreatedAtAsc(String roomCode);

    /**
     * 获取房间指定时间之后的消息（用于增量拉取）
     */
    List<RoomMessage> findByRoomCodeAndCreatedAtAfterOrderByCreatedAtAsc(String roomCode, LocalDateTime after);

    /**
     * 获取房间最近N条消息
     */
    @Query("SELECT m FROM RoomMessage m WHERE m.roomCode = :roomCode ORDER BY m.createdAt DESC LIMIT :limit")
    List<RoomMessage> findRecentMessages(String roomCode, int limit);

    /**
     * 删除指定房间的所有消息
     */
    @Modifying
    @Transactional
    void deleteByRoomCode(String roomCode);

    /**
     * 删除指定时间之前的所有消息（用于定时清理）
     */
    @Modifying
    @Transactional
    void deleteByCreatedAtBefore(LocalDateTime before);

    /**
     * 统计房间消息数量
     */
    long countByRoomCode(String roomCode);
}
