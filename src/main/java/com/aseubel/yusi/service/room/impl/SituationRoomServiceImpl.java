package com.aseubel.yusi.service.room.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.exception.AuthenticationException;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.utils.UuidUtils;
import com.aseubel.yusi.pojo.contant.RoomStatus;
import com.aseubel.yusi.pojo.dto.situation.SituationReport;
import com.aseubel.yusi.pojo.entity.SituationRoom;
import com.aseubel.yusi.pojo.entity.SituationScenario;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.SituationRoomRepository;
import com.aseubel.yusi.repository.SituationScenarioRepository;
import com.aseubel.yusi.service.room.SituationRoomService;
import com.aseubel.yusi.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SituationRoomServiceImpl implements SituationRoomService {

    private final SituationRoomRepository roomRepository;

    private final SituationScenarioRepository scenarioRepository;

    private final UserService userService;

    private final SituationReportService reportService;

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
            roomRepository.save(room); // Save status first

            // Trigger analysis asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    log.info("Starting async analysis for room: {}", room.getCode());
                    SituationReport report = reportService.analyze(room);

                    // Reload room to avoid stale object state issues (though in this simple case it
                    // might be okay, safer to reload)
                    roomRepository.findById(room.getCode()).ifPresent(r -> {
                        r.setReport(report);
                        roomRepository.save(r);
                        log.info("Async analysis completed for room: {}", room.getCode());
                    });
                } catch (Exception e) {
                    log.error("Async analysis failed for room: " + room.getCode(), e);
                }
            });
            return room; // Return immediately
        }
        return roomRepository.save(room);
    }

    @Override
    public List<SituationRoom> getHistory(String userId) {
        List<SituationRoom> rooms = roomRepository.findByMembersContainingOrderByCreatedAtDesc(userId);
        // Mask submissions for history to protect privacy
        return rooms.stream().map(room -> {
            SituationRoom safeRoom = room.toBuilder().build();
            if (room.getSubmissions() != null) {
                Map<String, String> maskedSubmissions = new ConcurrentHashMap<>();
                room.getSubmissions().forEach((k, v) -> {
                    if (k.equals(userId)) {
                        maskedSubmissions.put(k, v);
                    } else {
                        maskedSubmissions.put(k, "");
                    }
                });
                safeRoom.setSubmissions(maskedSubmissions);
            }
            return safeRoom;
        }).collect(Collectors.toList());
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
        populateMemberNames(room);

        // Populate scenario info if available
        if (room.getScenarioId() != null) {
            scenarioRepository.findById(room.getScenarioId()).ifPresent(room::setScenario);
        }
        if (ObjectUtil.isNotEmpty(room) && ObjectUtil.isNotEmpty(room.getReport())) {
            room.getReport().extractPublicSubmissions(room);
        }
        return room;
    }

    @Override
    public SituationRoom getRoomDetail(String code, String requesterId) {
        SituationRoom room = getRoom(code);
        SituationRoom safeRoom = room.toBuilder().build();

        if (room.getSubmissions() != null) {
            Map<String, String> maskedSubmissions = new ConcurrentHashMap<>();
            room.getSubmissions().forEach((k, v) -> {
                if (k.equals(requesterId)) {
                    maskedSubmissions.put(k, v);
                } else {
                    // Keep the key to show "Submitted", but mask the content
                    maskedSubmissions.put(k, "");
                }
            });
            safeRoom.setSubmissions(maskedSubmissions);
        }

        return safeRoom;
    }

    private void populateMemberNames(SituationRoom room) {
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
    }

    @Override
    public SituationScenario submitScenario(String userId, String title, String description) {
        SituationScenario scenario = new SituationScenario();
        scenario.setId(UuidUtils.genUuidSimple());
        scenario.setTitle(title);
        scenario.setDescription(description);
        scenario.setSubmitterId(userId);
        scenario.setStatus(0);
        return scenarioRepository.save(scenario);
    }

    @Override
    public SituationScenario reviewScenario(String adminId, String scenarioId, Integer status, String rejectReason) {
        if (!userService.checkAdmin(adminId)) {
            throw new AuthenticationException("无权限");
        }

        SituationScenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new BusinessException("Scenario not found"));
        scenario.setStatus(status);
        if (status == 1 || status == 2) {
            scenario.setRejectReason(rejectReason);
        }
        return scenarioRepository.save(scenario);
    }

    @Override
    public List<SituationScenario> getScenarios() {
        return scenarioRepository.findByStatusGreaterThanEqual(3);
    }

    @Override
    public List<SituationScenario> getScenariosByStatus(String userId, Integer status) {
        if (!userService.checkAdmin(userId)) {
            throw new AuthenticationException("无权限");
        }
        return scenarioRepository.findByStatus(status);
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
