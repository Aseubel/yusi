package com.aseubel.yusi.observability.health;

import com.aseubel.yusi.TestInfrastructureConfig;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestInfrastructureConfig.class)
class HealthEndpointExposureTest {

    private static final Path BASE_CONFIG = Path.of("src/main/resources/application.yml");
    private static final Path PROD_CONFIG = Path.of("src/main/resources/application-prod.yml");

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Autowired
    private HealthEndpointGroups healthEndpointGroups;

    @MockBean(name = "embeddingModel")
    private EmbeddingModel embeddingModel;

    @Test
    void managementSurfaceIsExplicitAndMinimal() throws IOException {
        String yaml = Files.readString(BASE_CONFIG) + "\n" + Files.readString(PROD_CONFIG);

        assertThat(yaml).contains("include: health,prometheus");
        assertThat(yaml).contains("show-details: never");
        assertThat(yaml).contains("port: ${MANAGEMENT_SERVER_PORT:20611}");
        assertThat(yaml).contains("address: ${MANAGEMENT_SERVER_ADDRESS:0.0.0.0}");
        assertThat(yaml).contains("liveness:");
        assertThat(yaml).contains("readiness:");
        assertThat(yaml).doesNotContain("include: *", "include: '*'");
        assertThat(yaml).doesNotContain("env", "beans", "configprops", "mappings", "loggers",
                "heapdump", "threaddump", "scheduledtasks", "conditions");
    }

    @Test
    void healthGroupsHaveExactDependencyMembershipAndLivenessIsIndependent() {
        assertThat(healthEndpointGroups.getNames()).containsExactlyInAnyOrder("liveness", "readiness");
        assertThat(healthEndpointGroups.get("liveness").isMember("livenessState")).isTrue();
        assertThat(healthEndpointGroups.get("liveness").isMember("db")).isFalse();

        assertThat(healthEndpointGroups.get("readiness").isMember("readinessState")).isTrue();
        assertThat(healthEndpointGroups.get("readiness").isMember("db")).isTrue();
        assertThat(healthEndpointGroups.get("readiness").isMember("redis")).isTrue();
        assertThat(healthEndpointGroups.get("readiness").isMember("milvus")).isTrue();
        assertThat(healthEndpointGroups.get("readiness").isMember("modelGateway")).isTrue();
        assertThat(healthEndpointGroups.get("readiness").isMember("tasks")).isTrue();

        HealthComponent liveness = healthEndpoint.healthForPath("liveness");
        assertThat(liveness.getStatus()).isEqualTo(Status.UP);
    }
}
