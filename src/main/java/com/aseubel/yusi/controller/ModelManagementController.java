package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.pojo.dto.model.GroupStrategySwitchRequest;
import com.aseubel.yusi.pojo.dto.model.ModelCallTraceItem;
import com.aseubel.yusi.pojo.dto.model.ModelCallTraceQuery;
import com.aseubel.yusi.pojo.dto.model.ModelGovernanceSnapshot;
import com.aseubel.yusi.pojo.dto.model.ModelGovernanceUpdateRequest;
import com.aseubel.yusi.pojo.dto.model.ModelMetricSummary;
import com.aseubel.yusi.pojo.dto.model.ModelRoutePreviewRequest;
import com.aseubel.yusi.pojo.dto.model.ModelRoutePreviewResponse;
import com.aseubel.yusi.service.ai.model.ModelManagementService;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import com.aseubel.yusi.service.ai.model.ModelSelectionStrategyType;
import com.aseubel.yusi.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/model")
@Auth
@Validated
@RequiredArgsConstructor
public class ModelManagementController {

    private final ModelManagementService modelManagementService;
    private final UserService userService;

    @GetMapping("/states")
    public Response<List<ModelRuntimeState>> states() {
        checkAdmin();
        return Response.success(modelManagementService.listModelStates());
    }

    @GetMapping("/groups/{group}/strategy")
    public Response<Map<String, String>> groupStrategy(@PathVariable String group) {
        checkAdmin();
        ModelSelectionStrategyType strategy = modelManagementService.getGroupStrategy(group);
        return Response.success(Map.of("group", group, "strategy", strategy.name()));
    }

    @PostMapping("/groups/strategy/switch")
    public Response<Map<String, String>> switchStrategy(@Valid @RequestBody GroupStrategySwitchRequest request) {
        checkAdmin();
        modelManagementService.switchGroupStrategy(request.getGroup(), request.getStrategy(), UserContext.getUserId());
        return Response.success(Map.of("group", request.getGroup(), "strategy", request.getStrategy().name()));
    }

    @GetMapping("/console")
    public Response<ModelGovernanceSnapshot> console() {
        checkAdmin();
        return Response.success(modelManagementService.getGovernanceSnapshot());
    }

    @PutMapping("/console")
    public Response<Map<String, Object>> updateConsole(@Valid @RequestBody ModelGovernanceUpdateRequest request) {
        checkAdmin();
        long version = modelManagementService.updateGovernance(request, UserContext.getUserId());
        return Response.success(Map.of("version", version, "status", "updated"));
    }

    @PostMapping("/routes/preview")
    public Response<ModelRoutePreviewResponse> previewRoute(@Valid @RequestBody ModelRoutePreviewRequest request) {
        checkAdmin();
        return Response.success(modelManagementService.previewRoute(request));
    }

    @GetMapping("/attempts")
    public Response<Page<ModelCallTraceItem>> attempts(@ModelAttribute ModelCallTraceQuery query) {
        checkAdmin();
        return Response.success(modelManagementService.queryAttempts(query));
    }

    @GetMapping("/metrics")
    public Response<ModelMetricSummary> metrics(@ModelAttribute ModelCallTraceQuery query) {
        checkAdmin();
        return Response.success(modelManagementService.getMetrics(query));
    }

    @GetMapping("/config")
    public Response<ModelRoutingProperties> config() {
        checkAdmin();
        return Response.success(modelManagementService.getModelConfigForDisplay());
    }

    @PutMapping("/config")
    public Response<Map<String, String>> updateConfig(@RequestBody ModelRoutingProperties request) {
        checkAdmin();
        try {
            modelManagementService.updateModelConfig(request);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, e.getMessage());
        }
        return Response.success(Map.of("status", "updated"));
    }

    private void checkAdmin() {
        String userId = UserContext.getUserId();
        if (!userService.checkAdmin(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Permission denied: Admin access required");
        }
    }
}
