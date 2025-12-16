package com.aseubel.yusi.service.room;

import com.aseubel.yusi.pojo.dto.situation.SituationReport;
import com.aseubel.yusi.pojo.entity.SituationRoom;

public interface SituationRoomService {
    SituationRoom createRoom(String ownerId, int maxMembers);
    SituationRoom joinRoom(String code, String userId);
    SituationRoom startRoom(String code, String scenarioId, String ownerId);
    SituationRoom submit(String code, String userId, String narrative);
    SituationReport getReport(String code);
}