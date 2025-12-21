package com.aseubel.yusi.service.room.impl;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.pojo.dto.situation.SituationReport;
import com.aseubel.yusi.service.room.SituationRoomService;
import com.aseubel.yusi.pojo.contant.RoomStatus;
import com.aseubel.yusi.pojo.entity.SituationRoom;
import com.aseubel.yusi.repository.SituationRoomRepository;
import com.aseubel.yusi.repository.SituationScenarioRepository;
import com.aseubel.yusi.service.user.UserService;
import com.aseubel.yusi.pojo.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class SituationRoomServiceImpl implements SituationRoomService {

    @Autowired
    private SituationRoomRepository roomRepository;

    @Autowired
    private SituationScenarioRepository scenarioRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private SituationReportService reportService;

    @Override
    public SituationRoom createRoom(String ownerId, int maxMembers) {
        if (scenarioRepository.count() == 0) {
            throw new BusinessException("暂无情景可用，请联系管理员添加");
        }
        String code = generateCode();
        SituationRoom room = SituationRoom.builder()
                .code(code)
                .status(RoomStatus.WAITING)
                .ownerId(ownerId)
                .members(ConcurrentHashMap.newKeySet())
                .submissions(new ConcurrentHashMap<>())
                .createdAt(LocalDateTime.now())
                .build();
        room.getMembers().add(ownerId);
        return roomRepository.save(room);
    }

    @Override
    public SituationRoom joinRoom(String code, String userId) {
        SituationRoom room = getRoom(code);
        if (room.getStatus() != RoomStatus.WAITING)
            throw new BusinessException("房间不可加入");
        if (room.getMembers().size() >= 8)
            throw new BusinessException("人数已满");
        room.getMembers().add(userId);
        return roomRepository.save(room);
    }

    @Override
    public SituationRoom startRoom(String code, String scenarioId, String ownerId) {
        SituationRoom room = getRoom(code);
        if (room.getStatus() != RoomStatus.WAITING)
            throw new BusinessException("房间状态错误");
        if (!room.getMembers().contains(ownerId))
            throw new BusinessException("非房主");
        // Verify ownerId matches
        if (room.getOwnerId() != null && !room.getOwnerId().equals(ownerId))
            throw new BusinessException("非房主");

        if (room.getMembers().size() < 2)
            throw new BusinessException("至少2人");
        room.setScenarioId(scenarioId);
        room.setStatus(RoomStatus.IN_PROGRESS);
        return roomRepository.save(room);
    }

    @Override
    public void cancelRoom(String code, String userId) {
        SituationRoom room = roomRepository.findById(code).orElseThrow(() -> new BusinessException("房间不存在"));
        if (!room.getOwnerId().equals(userId)) {
            throw new BusinessException("非房主不可解散房间");
        }
        room.setStatus(RoomStatus.CANCELLED);
        roomRepository.save(room);
    }

    @Override
    public SituationRoom voteCancel(String code, String userId) {
        SituationRoom room = getRoom(code);
        if (room.getStatus() != RoomStatus.IN_PROGRESS) {
            throw new BusinessException("房间未进行中，无法投票解散");
        }
        if (!room.getMembers().contains(userId)) {
            throw new BusinessException("非房间成员");
        }
        if (room.getCancelVotes() == null) {
            room.setCancelVotes(ConcurrentHashMap.newKeySet());
        }
        room.getCancelVotes().add(userId);

        // Check if votes > members / 2
        if (room.getCancelVotes().size() > room.getMembers().size() / 2) {
            room.setStatus(RoomStatus.CANCELLED);
        }
        return roomRepository.save(room);
    }

    @Override
    public SituationRoom submit(String code, String userId, String narrative, Boolean isPublic) {
        SituationRoom room = getRoom(code);
        if (room.getStatus() != RoomStatus.IN_PROGRESS)
            throw new BusinessException("未开始或已结束");
        if (!room.getMembers().contains(userId))
            throw new BusinessException("非房间成员");
        if (room.getSubmissions().containsKey(userId))
            throw new BusinessException("已提交");
        if (narrative == null || narrative.length() > 1000)
            throw new BusinessException("叙事长度不合法");

        room.getSubmissions().put(userId, narrative);
        if (room.getSubmissionVisibility() == null) {
            room.setSubmissionVisibility(new ConcurrentHashMap<>());
        }
        room.getSubmissionVisibility().put(userId, isPublic != null && isPublic);

        if (room.allSubmitted()) {
            room.setStatus(RoomStatus.COMPLETED);
            // Trigger analysis immediately when completed
            try {
                SituationReport report = reportService.analyze(room);
                room.setReport(report);
            } catch (Exception e) {
                log.error("Auto analysis failed", e);
            }
        }
        return roomRepository.save(room);
    }

    @Override
    public java.util.List<SituationRoom> getHistory(String userId) {
        return roomRepository.findByMembersContainingOrderByCreatedAtDesc(userId);
    }

    @Override
    public SituationReport getReport(String code) {
        SituationRoom room = getRoom(code);
        if (room.getStatus() != RoomStatus.COMPLETED)
            throw new BusinessException("未完成");
        if (room.getReport() != null) {
            return room.getReport();
        }
        SituationReport report = reportService.analyze(room);
        room.setReport(report);
        roomRepository.save(room);
        return report;
    }

    @Override
    public SituationRoom getRoom(String code) {
        SituationRoom room = roomRepository.findById(code).orElseThrow(() -> new BusinessException("房间不存在"));

        // Populate member names
        if (room.getMembers() != null) {
            room.setMemberNames(new HashMap<>());
            for (String memberId : room.getMembers()) {
                try {
                    User u = userService.getUserByUserId(memberId);
                    if (u != null) {
                        room.getMemberNames().put(memberId, u.getUserName());
                    } else {
                        room.getMemberNames().put(memberId, "未知用户");
                    }
                } catch (Exception e) {
                    room.getMemberNames().put(memberId, "未知用户");
                }
            }
        }

        // Populate scenario info if available
        if (room.getScenarioId() != null) {
            scenarioRepository.findById(room.getScenarioId()).ifPresent(room::setScenario);
        }
        return room;
    }

    @Override
    public java.util.List<com.aseubel.yusi.pojo.entity.SituationScenario> getScenarios() {
        return scenarioRepository.findAll();
    }

    private String generateCode() {
        String letters = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++)
            sb.append(letters.charAt(ThreadLocalRandom.current().nextInt(letters.length())));
        String code = sb.toString();
        if (roomRepository.existsById(code))
            return generateCode();
        return code;
    }
}