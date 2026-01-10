package com.aseubel.yusi.service.room;

import com.aseubel.yusi.pojo.dto.situation.SituationReport;
import com.aseubel.yusi.pojo.entity.SituationRoom;
import com.aseubel.yusi.pojo.entity.SituationScenario;

import java.util.List;

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

    List<SituationScenario> getScenarios();

    List<SituationScenario> getScenariosByStatus(String userId, Integer status);

    SituationScenario submitScenario(String userId, String title, String description);

    SituationScenario reviewScenario(String adminId, String scenarioId, Integer status, String rejectReason);

    List<SituationRoom> getHistory(String userId);
}