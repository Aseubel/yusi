package com.aseubel.yusi.service.room;

import com.aseubel.yusi.pojo.constant.SecurityAuditAction;
import com.aseubel.yusi.pojo.constant.SecurityAuditDetailKeys;
import com.aseubel.yusi.pojo.constant.SecurityAuditOutcome;
import com.aseubel.yusi.pojo.constant.SecurityAuditOperation;
import com.aseubel.yusi.pojo.constant.SecurityAuditReasonCode;
import com.aseubel.yusi.pojo.constant.SecurityAuditResourceType;
import com.aseubel.yusi.pojo.entity.SituationScenario;
import com.aseubel.yusi.redis.service.IRedisService;
import com.aseubel.yusi.repository.SituationRoomRepository;
import com.aseubel.yusi.repository.SituationScenarioRepository;
import com.aseubel.yusi.service.room.impl.SituationReportService;
import com.aseubel.yusi.service.room.impl.SituationRoomServiceImpl;
import com.aseubel.yusi.service.security.SecurityAuditService;
import com.aseubel.yusi.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SituationRoomServiceAuditTest {

    @Mock
    private SituationRoomRepository roomRepository;
    @Mock
    private SituationScenarioRepository scenarioRepository;
    @Mock
    private UserService userService;
    @Mock
    private SituationReportService reportService;
    @Mock
    private IRedisService redisService;
    @Mock
    private ThreadPoolTaskExecutor threadPoolExecutor;
    @Mock
    private SecurityAuditService securityAuditService;

    @InjectMocks
    private SituationRoomServiceImpl situationRoomService;

    @Test
    void legacyAdminReviewCreatesTheSameScenarioAuditAsAdminEndpoint() {
        SituationScenario scenario = new SituationScenario();
        scenario.setId("scenario-1");
        scenario.setSubmitterId("user-1");
        scenario.setStatus(SituationScenario.STATUS_PENDING);
        when(userService.checkAdmin("admin-1")).thenReturn(true);
        when(scenarioRepository.findById("scenario-1")).thenReturn(Optional.of(scenario));
        when(scenarioRepository.save(scenario)).thenReturn(scenario);

        SituationScenario result = situationRoomService.reviewScenario(
                "admin-1", "scenario-1", SituationScenario.STATUS_MANUAL_APPROVED, null);

        assertEquals(SituationScenario.STATUS_MANUAL_APPROVED, result.getStatus());
        ArgumentCaptor<Map<String, String>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(securityAuditService).recordAdmin(
                eq(SecurityAuditAction.SCENARIO_REVIEWED),
                eq("admin-1"),
                eq("user-1"),
                eq(SecurityAuditResourceType.SITUATION_SCENARIO),
                eq("scenario-1"),
                eq(SecurityAuditOutcome.SUCCESS),
                eq(SecurityAuditReasonCode.ADMIN_MUTATION),
                detailsCaptor.capture());
        assertThat(detailsCaptor.getValue())
                .containsEntry(SecurityAuditDetailKeys.OPERATION, SecurityAuditOperation.REVIEW.name())
                .containsEntry(SecurityAuditDetailKeys.ACTION, "APPROVE")
                .containsEntry(SecurityAuditDetailKeys.FROM_STATUS, String.valueOf(SituationScenario.STATUS_PENDING))
                .containsEntry(SecurityAuditDetailKeys.TO_STATUS, String.valueOf(SituationScenario.STATUS_MANUAL_APPROVED));
    }
}
