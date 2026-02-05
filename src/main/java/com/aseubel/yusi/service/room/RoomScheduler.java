package com.aseubel.yusi.service.room;

import com.aseubel.yusi.pojo.contant.RoomStatus;
import com.aseubel.yusi.pojo.entity.SituationRoom;
import com.aseubel.yusi.repository.SituationRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 房间定时任务
 * - 自动解散超时未开始的房间（WAITING状态超过10分钟）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomScheduler {

    private final SituationRoomRepository roomRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 房间超时时间（分钟）
     */
    private static final int ROOM_TIMEOUT_MINUTES = 10;

    /**
     * 每分钟检查一次超时房间
     */
    @Scheduled(fixedRate = 60000)
    public void dissolveExpiredRooms() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(ROOM_TIMEOUT_MINUTES);
        List<SituationRoom> expiredRooms = roomRepository.findExpiredWaitingRooms(threshold);

        for (SituationRoom room : expiredRooms) {
            try {
                room.setStatus(RoomStatus.CANCELLED);
                roomRepository.save(room);

                // 通过 WebSocket 通知前端房间已被自动解散
                messagingTemplate.convertAndSend("/topic/room/" + room.getCode() + "/status",
                        new RoomTimeoutEvent(room.getCode(), "TIMEOUT_DISSOLVED"));

                log.info("Auto-dissolved expired room: {}, created at: {}", room.getCode(), room.getCreatedAt());
            } catch (Exception e) {
                log.error("Failed to dissolve room: {}", room.getCode(), e);
            }
        }

        if (!expiredRooms.isEmpty()) {
            log.info("Dissolved {} expired rooms", expiredRooms.size());
        }
    }

    /**
     * 房间超时事件
     */
    public record RoomTimeoutEvent(String roomCode, String reason) {
    }
}
