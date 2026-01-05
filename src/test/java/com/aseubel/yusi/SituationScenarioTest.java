package com.aseubel.yusi;

import com.aseubel.yusi.pojo.entity.SituationScenario;
import com.aseubel.yusi.repository.SituationScenarioRepository;
import com.aseubel.yusi.service.room.SituationRoomService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "yusi.admin.userId=admin",
    "model.embedding.baseurl=http://localhost",
    "model.embedding.apikey=dummy",
    "model.embedding.model=dummy",
    "model.chat.baseurl=http://localhost",
    "model.chat.apikey=dummy",
    "model.chat.model=dummy",
    "YUSI_ENCRYPTION_KEY=1234567890123456",
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SituationScenarioTest {

    @Autowired
    private SituationRoomService situationRoomService;

    @Autowired
    private SituationScenarioRepository scenarioRepository;

    @MockBean(name = "milvusEmbeddingStore")
    private MilvusEmbeddingStore milvusEmbeddingStore;

    @MockBean(name = "embeddingModel")
    private EmbeddingModel embeddingModel;

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
