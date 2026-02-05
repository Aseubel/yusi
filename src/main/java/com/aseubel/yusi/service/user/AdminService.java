package com.aseubel.yusi.service.user;

import com.aseubel.yusi.pojo.dto.admin.AdminStatsResponse;
import com.aseubel.yusi.pojo.dto.admin.ScenarioAuditRequest;
import com.aseubel.yusi.pojo.entity.SituationScenario;
import com.aseubel.yusi.pojo.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminService {

    AdminStatsResponse getStats();

    Page<User> getUsers(Pageable pageable, String search);

    void updateUserPermission(String userId, Integer permissionLevel);

    Page<SituationScenario> getPendingScenarios(Pageable pageable);

    void auditScenario(String scenarioId, ScenarioAuditRequest request);
}
