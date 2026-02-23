package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    /**
     * 查询用户的所有消息（分页）
     */
    Page<UserNotification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * 查询用户指定类型的消息
     */
    List<UserNotification> findByUserIdAndTypeOrderByCreatedAtDesc(String userId, String type);

    /**
     * 查询用户的未读消息
     */
    List<UserNotification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(String userId);

    /**
     * 统计用户未读消息数量
     */
    long countByUserIdAndIsReadFalse(String userId);

    /**
     * 标记消息为已读
     */
    @Modifying
    @Query("UPDATE UserNotification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.id = :id")
    int markAsRead(@Param("id") Long id);

    /**
     * 标记用户所有消息为已读
     */
    @Modifying
    @Query("UPDATE UserNotification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") String userId);

    /**
     * 删除用户的所有消息
     */
    void deleteByUserId(String userId);
}
