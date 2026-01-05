package com.aseubel.yusi.service.room;

import com.aseubel.yusi.pojo.dto.situation.SituationReport;
import com.aseubel.yusi.pojo.entity.SituationRoom;

public interface SituationRoomService {
    SituationRoom createRoom(String ownerId, int maxMembers);

    SituationRoom joinRoom(String code, String userId);

    SituationRoom startRoom(String code, String scenarioId, String ownerId);

    void cancelRoom(String code, String userId);

    SituationRoom voteCancel(String code, String userId);

    SituationRoom submit(String code, String userId, String narrative, Boolean isPublic);

    SituationRoom getRoom(String code);

    SituationRoom getRoomDetail(String code, String requesterId);

    SituationReport getReport(String code);

    java.util.List<com.aseubel.yusi.pojo.entity.SituationScenario> getScenarios();

    com.aseubel.yusi.pojo.entity.SituationScenario submitScenario(String userId, String title, String description);

    com.aseubel.yusi.pojo.entity.SituationScenario reviewScenario(String adminId, String scenarioId, Integer status, String rejectReason);

    java.util.List<SituationRoom> getHistory(String userId);
}