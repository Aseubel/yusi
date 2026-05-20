package com.aseubel.yusi;

import com.aseubel.yusi.pojo.entity.SituationScenario;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.SituationScenarioRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.room.SituationRoomService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SituationScenarioTest {

    @Autowired
    private SituationRoomService situationRoomService;

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
        SituationScenario reviewed = situationRoomService.reviewScenario(adminId, submitted.getId(), 4, null);
        assertEquals(4, reviewed.getStatus());

        // 3. Get Scenarios (should include it now as status >= 3)
        List<SituationScenario> scenarios = situationRoomService.getScenarios();
        assertTrue(scenarios.stream().anyMatch(s -> s.getId().equals(submitted.getId())));

        // 4. Review (Reject - Status 1: Manual Reject)
        situationRoomService.reviewScenario(adminId, submitted.getId(), 1, "Bad content");

        // 5. Get Scenarios (should NOT include it)
        scenarios = situationRoomService.getScenarios();
        assertFalse(scenarios.stream().anyMatch(s -> s.getId().equals(submitted.getId())));

        // Cleanup
        scenarioRepository.deleteById(submitted.getId());
    }
}
