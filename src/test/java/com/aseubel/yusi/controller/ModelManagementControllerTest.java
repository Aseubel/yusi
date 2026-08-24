package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.pojo.dto.model.ModelGovernanceSnapshot;
import com.aseubel.yusi.pojo.dto.model.ModelRoutePreviewRequest;
import com.aseubel.yusi.pojo.dto.model.ModelRoutePreviewResponse;
import com.aseubel.yusi.pojo.dto.model.ModelMetricTrendQuery;
import com.aseubel.yusi.pojo.dto.model.ModelMetricTrendResponse;
import com.aseubel.yusi.service.ai.model.ModelManagementService;
import com.aseubel.yusi.service.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelManagementControllerTest {

    private final ModelManagementService service = mock(ModelManagementService.class);
    private final UserService userService = mock(UserService.class);
    private final ModelManagementController controller = new ModelManagementController(service, userService);

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void rejectsNonAdminUsersForTheGovernanceConsole() {
        UserContext.setUserId("user-1");
        when(userService.checkAdmin("user-1")).thenReturn(false);

        assertThatThrownBy(controller::console)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Admin");
    }

    @Test
    void consoleContainsApiKeyStatusButNeverTheSecretField() throws Exception {
        UserContext.setUserId("admin-1");
        when(userService.checkAdmin("admin-1")).thenReturn(true);
        when(service.getGovernanceSnapshot()).thenReturn(ModelGovernanceSnapshot.builder()
                .version(3L)
                .schemaVersion(2)
                .models(List.of(ModelGovernanceSnapshot.ModelGovernanceModel.builder()
                        .id("qwen")
                        .provider("openai-compatible")
                        .apiKeyConfigured(true)
                        .build()))
                .build());

        Response<ModelGovernanceSnapshot> response = controller.console();
        String json = new ObjectMapper().writeValueAsString(response.getData());

        assertThat(response.getData().getVersion()).isEqualTo(3L);
        assertThat(response.getData().getModels().getFirst().isApiKeyConfigured()).isTrue();
        assertThat(json).doesNotContain("\"apikey\"").doesNotContain("test-secret");
    }

    @Test
    void previewUsesTheGovernanceServiceContract() {
        UserContext.setUserId("admin-1");
        when(userService.checkAdmin("admin-1")).thenReturn(true);
        ModelRoutePreviewResponse preview = ModelRoutePreviewResponse.builder()
                .policyId("chat-zh")
                .primaryTier("balanced")
                .routeReason("policy=chat-zh")
                .build();
        when(service.previewRoute(any(ModelRoutePreviewRequest.class))).thenReturn(preview);

        Response<ModelRoutePreviewResponse> response = controller.previewRoute(
                ModelRoutePreviewRequest.builder().scene("chat").build());

        assertThat(response.getData().getPolicyId()).isEqualTo("chat-zh");
        verify(service).previewRoute(any(ModelRoutePreviewRequest.class));
    }

    @Test
    void metricTrendRequiresAdminAndDelegatesTheFilter() {
        UserContext.setUserId("user-1");
        when(userService.checkAdmin("user-1")).thenReturn(false);

        assertThatThrownBy(() -> controller.metricTrend(new ModelMetricTrendQuery()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Admin");

        UserContext.setUserId("admin-1");
        when(userService.checkAdmin("admin-1")).thenReturn(true);
        when(service.getMetricTrend(any(ModelMetricTrendQuery.class))).thenReturn(
                ModelMetricTrendResponse.builder()
                        .bucket(ModelMetricTrendQuery.Bucket.HOUR)
                        .items(List.of())
                        .build());

        assertThat(controller.metricTrend(new ModelMetricTrendQuery()).getData().bucket())
                .isEqualTo(ModelMetricTrendQuery.Bucket.HOUR);
        verify(service).getMetricTrend(any(ModelMetricTrendQuery.class));
    }
}
