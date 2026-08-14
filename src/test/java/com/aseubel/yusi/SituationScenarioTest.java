package com.aseubel.yusi;

import com.aseubel.yusi.pojo.entity.SituationScenario;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.pojo.dto.admin.ScenarioAuditRequest;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.repository.SituationScenarioRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.room.SituationRoomService;
import com.aseubel.yusi.service.user.AdminService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestInfrastructureConfig.class)
class SituationScenarioTest {

    @Autowired
    private SituationRoomService situationRoomService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private SituationScenarioRepository scenarioRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean(name = "milvusClientV2")
    private MilvusClientV2 milvusClientV2;

    @MockBean(name = "embeddingModel")
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void setUpAdmin() {
        if (userRepository.findByUserId("admin") == null) {
            userRepository.save(User.builder()
                    .userId("admin")
                    .userName("admin")
                    .password("password")
                    .permissionLevel(10)
                    .build());
        }
    }

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void testSubmitAndReviewScenario() {
        String userId = "testUser";
        String adminId = "admin";

        // 1. Submit
        SituationScenario submitted = situationRoomService.submitScenario(userId, "Test Scenario", "Description");
        assertNotNull(submitted.getId());
        assertEquals(0, submitted.getStatus());
        assertEquals(userId, submitted.getSubmitterId());

        // 2. Review (Approve - Status 3: AI Passed, or 4: Manual Passed)
        // Using 4 for manual pass
        UserContext.setUserId(adminId);
        ScenarioAuditRequest approveRequest = new ScenarioAuditRequest();
        approveRequest.setApproved(true);
        adminService.auditScenario(submitted.getId(), approveRequest);
        SituationScenario reviewed = scenarioRepository.findById(submitted.getId()).orElseThrow();
        assertEquals(SituationScenario.STATUS_MANUAL_APPROVED, reviewed.getStatus());

        // 3. Get Scenarios (should include it now as status >= 3)
        List<SituationScenario> scenarios = situationRoomService.getScenarios();
        assertTrue(scenarios.stream().anyMatch(s -> s.getId().equals(submitted.getId())));

        // 4. Review (Reject - Status 1: Manual Reject)
        ScenarioAuditRequest rejectRequest = new ScenarioAuditRequest();
        rejectRequest.setRejectReason("Bad content");
        adminService.auditScenario(submitted.getId(), rejectRequest);

        // 5. Get Scenarios (should NOT include it)
        scenarios = situationRoomService.getScenarios();
        assertFalse(scenarios.stream().anyMatch(s -> s.getId().equals(submitted.getId())));

        // Cleanup
        scenarioRepository.deleteById(submitted.getId());
    }
}
