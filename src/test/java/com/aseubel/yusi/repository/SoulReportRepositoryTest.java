package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.SoulReport;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SoulReportRepositoryTest {

    @Autowired
    private SoulReportRepository soulReportRepository;

    @MockBean(name = "embeddingModel")
    private EmbeddingModel embeddingModel;

    @MockBean(name = "milvusClientV2")
    private MilvusClientV2 milvusClientV2;

    @Test
    void testSoulReportJpaAuditing() {
        SoulReport report = SoulReport.builder()
                .userId("test-user-id")
                .reportType("WEEKLY")
                .title("Test Weekly Report")
                .content("Test Content")
                .periodStart(LocalDate.now().minusDays(7))
                .periodEnd(LocalDate.now())
                .notified(false)
                .build();

        // Save entity - JpaAuditing should automatically set createdAt field
        SoulReport savedReport = soulReportRepository.save(report);

        try {
            assertThat(savedReport.getId()).isNotNull();
            assertThat(savedReport.getCreatedAt()).isNotNull();
            
            // Retrieve from database and verify
            SoulReport retrieved = soulReportRepository.findById(savedReport.getId()).orElseThrow();
            assertThat(retrieved.getCreatedAt()).isNotNull();
            assertThat(retrieved.getCreatedAt()).isEqualTo(savedReport.getCreatedAt());
        } finally {
            // Clean up
            soulReportRepository.delete(savedReport);
        }
    }
}
