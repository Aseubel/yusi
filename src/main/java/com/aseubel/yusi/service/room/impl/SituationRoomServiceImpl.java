package com.aseubel.yusi.service.room.impl;

import com.aseubel.yusi.pojo.dto.situation.SituationReport;
import com.aseubel.yusi.service.room.SituationRoomService;
import com.aseubel.yusi.pojo.contant.RoomStatus;
import com.aseubel.yusi.pojo.entity.SituationRoom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SituationRoomServiceImpl implements SituationRoomService {

    private final Map<String, SituationRoom> store = new ConcurrentHashMap<>();

    @Autowired
    private SituationReportService reportService;

    @Override
    public SituationRoom createRoom(String ownerId, int maxMembers) {
        String code = generateCode();
        SituationRoom room = SituationRoom.builder()
                .code(code)
                .status(RoomStatus.WAITING)
                .members(ConcurrentHashMap.newKeySet())
                .submissions(new ConcurrentHashMap<>())
                .createdAt(LocalDateTime.now())
                .build();
        room.getMembers().add(ownerId);
        store.put(code, room);
        return room;
    }

    @Override
    public SituationRoom joinRoom(String code, String userId) {
        SituationRoom room = get(code);
        if (room.getStatus() != RoomStatus.WAITING) throw new IllegalStateException("房间不可加入");
        if (room.getMembers().size() >= 8) throw new IllegalStateException("人数已满");
        room.getMembers().add(userId);
        return room;
    }

    @Override
    public SituationRoom startRoom(String code, String scenarioId, String ownerId) {
        SituationRoom room = get(code);
        if (room.getStatus() != RoomStatus.WAITING) throw new IllegalStateException("房间状态错误");
        if (!room.getMembers().contains(ownerId)) throw new IllegalStateException("非房主");
        if (room.getMembers().size() < 2) throw new IllegalStateException("至少2人");
        room.setScenarioId(scenarioId);
        room.setStatus(RoomStatus.IN_PROGRESS);
        return room;
    }

    @Override
    public SituationRoom submit(String code, String userId, String narrative) {
        SituationRoom room = get(code);
        if (room.getStatus() != RoomStatus.IN_PROGRESS) throw new IllegalStateException("未开始或已结束");
        if (!room.getMembers().contains(userId)) throw new IllegalStateException("非房间成员");
        if (room.getSubmissions().containsKey(userId)) throw new IllegalStateException("已提交");
        if (narrative == null || narrative.length() > 1000) throw new IllegalArgumentException("叙事长度不合法");
        room.getSubmissions().put(userId, narrative);
        if (room.allSubmitted()) room.setStatus(RoomStatus.COMPLETED);
        return room;
    }

    @Override
    public SituationReport getReport(String code) {
        SituationRoom room = get(code);
        if (room.getStatus() != RoomStatus.COMPLETED) throw new IllegalStateException("未完成");
        if (room.getReport() != null) {
            return room.getReport();
        }
        SituationReport report = reportService.analyze(room);
        room.setReport(report);
        return report;
    }

    private SituationRoom get(String code) {
        SituationRoom room = store.get(code);
        if (room == null) throw new IllegalArgumentException("房间不存在");
        return room;
    }

    private String generateCode() {
        String letters = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(letters.charAt(ThreadLocalRandom.current().nextInt(letters.length())));
        String code = sb.toString();
        if (store.containsKey(code)) return generateCode();
        return code;
    }
}