package com.aseubel.yusi.service.room.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.exception.AuthenticationException;
import com.aseubel.yusi.common.exception.AuthenticationException;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.common.utils.UuidUtils;
import com.aseubel.yusi.pojo.contant.RoomStatus;
import com.aseubel.yusi.pojo.dto.situation.SituationReport;
import com.aseubel.yusi.pojo.dto.situation.SituationRoomDetailResponse;
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
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "暂无情景可用，请联系管理员添加");
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
            throw new BusinessException(ErrorCode.FORBIDDEN, "房间不可加入");
        if (room.getMembers().size() >= 8)
            throw new BusinessException(ErrorCode.FORBIDDEN, "人数已满");
        room.getMembers().add(userId);
        return roomRepository.save(room);
    }

    @Override
    public SituationRoom startRoom(String code, String scenarioId, String ownerId) {
        SituationRoom room = getRoom(code);
        if (room.getStatus() != RoomStatus.WAITING)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "房间状态错误");
        if (!room.getMembers().contains(ownerId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "非房主");
        // Verify ownerId matches
        if (room.getOwnerId() != null && !room.getOwnerId().equals(ownerId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "非房主");

        if (room.getMembers().size() < 2)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "至少2人");
        room.setScenarioId(scenarioId);
        room.setStatus(RoomStatus.IN_PROGRESS);
        return roomRepository.save(room);
    }

    @Override
    public void cancelRoom(String code, String userId) {
        SituationRoom room = roomRepository.findById(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "房间不存在"));
        if (!room.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "非房主不可解散房间");
        }
        room.setStatus(RoomStatus.CANCELLED);
        roomRepository.save(room);
    }

    @Override
    public SituationRoom voteCancel(String code, String userId) {
        SituationRoom room = getRoom(code);
        if (room.getStatus() != RoomStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "房间未进行中，无法投票解散");
        }
        if (!room.getMembers().contains(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "非房间成员");
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
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未开始或已结束");
        if (!room.getMembers().contains(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "非房间成员");
        if (room.getSubmissions().containsKey(userId))
            throw new BusinessException(ErrorCode.PARAM_ERROR, "已提交");
        if (narrative == null || narrative.length() > 1000)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "叙事长度不合法");

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
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未完成");
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
        SituationRoom room = roomRepository.findById(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "房间不存在"));

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
                    maskedSubmissions.put(k, "");
                }
            });
            safeRoom.setSubmissions(maskedSubmissions);
        }

        return safeRoom;
    }

    @Override
    public SituationRoomDetailResponse getRoomDetailResponse(String code, String requesterId) {
        SituationRoom room = getRoom(code);

        SituationRoomDetailResponse.ScenarioDetail scenarioDetail = null;
        if (room.getScenario() != null) {
            SituationScenario scenario = room.getScenario();
            scenarioDetail = SituationRoomDetailResponse.ScenarioDetail.builder()
                    .id(scenario.getId())
                    .title(scenario.getTitle())
                    .description(scenario.getDescription())
                    .summary(generateSummary(scenario.getDescription()))
                    .build();
        }

        Map<String, String> maskedSubmissions = new ConcurrentHashMap<>();
        if (room.getSubmissions() != null) {
            room.getSubmissions().forEach((k, v) -> {
                if (k.equals(requesterId)) {
                    maskedSubmissions.put(k, v);
                } else {
                    maskedSubmissions.put(k, "");
                }
            });
        }

        return SituationRoomDetailResponse.builder()
                .code(room.getCode())
                .status(room.getStatus())
                .ownerId(room.getOwnerId())
                .scenarioId(room.getScenarioId())
                .members(room.getMembers())
                .submissions(maskedSubmissions)
                .submissionVisibility(room.getSubmissionVisibility())
                .cancelVotes(room.getCancelVotes())
                .createdAt(room.getCreatedAt())
                .report(room.getReport())
                .memberNames(room.getMemberNames())
                .scenario(scenarioDetail)
                .build();
    }

    private String generateSummary(String description) {
        if (description == null || description.isEmpty()) {
            return "";
        }
        int length = description.length();
        if (length <= 80) {
            return description;
        }
        int endIndex = Math.min(80, length);
        String summary = description.substring(0, endIndex);
        if (endIndex < length && !summary.endsWith(" ") && !summary.endsWith("\n")) {
            int lastSpace = summary.lastIndexOf(' ');
            int lastNewline = summary.lastIndexOf('\n');
            int cutPoint = Math.max(lastSpace, lastNewline);
            if (cutPoint > 0) {
                summary = summary.substring(0, cutPoint);
            }
        }
        return summary + "...";
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
        scenario.setStatus(SituationScenario.STATUS_PENDING);
        return scenarioRepository.save(scenario);
    }

    @Override
    public SituationScenario updateScenario(String userId, String scenarioId, String title, String description) {
        SituationScenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "情景不存在"));
        if (!scenario.getSubmitterId().equals(userId)) {
            throw new AuthenticationException("无权限修改此情景");
        }
        if (scenario.getStatus() == SituationScenario.STATUS_DELETED) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "情景已被删除");
        }
        scenario.setTitle(title);
        scenario.setDescription(description);
        scenario.setStatus(SituationScenario.STATUS_PENDING);
        scenario.setRejectReason(null);
        return scenarioRepository.save(scenario);
    }

    @Override
    public void deleteScenario(String userId, String scenarioId) {
        SituationScenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "情景不存在"));
        if (!scenario.getSubmitterId().equals(userId)) {
            throw new AuthenticationException("无权限删除此情景");
        }
        scenario.setStatus(SituationScenario.STATUS_DELETED);
        scenarioRepository.save(scenario);
    }

    @Override
    public SituationScenario resubmitScenario(String userId, String scenarioId) {
        SituationScenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "情景不存在"));
        if (!scenario.getSubmitterId().equals(userId)) {
            throw new AuthenticationException("无权限操作此情景");
        }
        if (scenario.getStatus() == SituationScenario.STATUS_DELETED) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "情景已被删除");
        }
        scenario.setStatus(SituationScenario.STATUS_PENDING);
        scenario.setRejectReason(null);
        return scenarioRepository.save(scenario);
    }

    @Override
    public List<SituationScenario> getMyScenarios(String userId) {
        return scenarioRepository.findBySubmitterIdAndStatusNot(userId, SituationScenario.STATUS_DELETED);
    }

    @Override
    public SituationScenario reviewScenario(String adminId, String scenarioId, Integer status, String rejectReason) {
        if (!userService.checkAdmin(adminId)) {
            throw new AuthenticationException("无权限");
        }

        SituationScenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Scenario not found"));
        scenario.setStatus(status);
        if (status == SituationScenario.STATUS_MANUAL_REJECTED || status == SituationScenario.STATUS_AI_REJECTED) {
            scenario.setRejectReason(rejectReason);
        }
        return scenarioRepository.save(scenario);
    }

    @Override
    public List<SituationScenario> getScenarios() {
        return scenarioRepository.findByStatusGreaterThanEqual(SituationScenario.STATUS_AI_APPROVED);
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
