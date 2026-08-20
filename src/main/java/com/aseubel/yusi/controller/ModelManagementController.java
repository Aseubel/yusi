package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.ratelimit.LimitType;
import com.aseubel.yusi.common.ratelimit.RateLimiter;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.dto.model.ModelCallTraceItem;
import com.aseubel.yusi.pojo.dto.model.ModelCallTraceQuery;
import com.aseubel.yusi.pojo.dto.model.ModelGovernanceSnapshot;
import com.aseubel.yusi.pojo.dto.model.ModelGovernanceUpdateRequest;
import com.aseubel.yusi.pojo.dto.model.ModelMetricSummary;
import com.aseubel.yusi.pojo.dto.model.ModelRoutePreviewRequest;
import com.aseubel.yusi.pojo.dto.model.ModelRoutePreviewResponse;
import com.aseubel.yusi.service.ai.model.ModelManagementService;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
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

    @GetMapping("/console")
    public Response<ModelGovernanceSnapshot> console() {
        checkAdmin();
        return Response.success(modelManagementService.getGovernanceSnapshot());
    }

    @PutMapping("/console")
    @RateLimiter(key = "model-console-update", time = 60, count = 10, limitType = LimitType.USER)
    public Response<Map<String, Object>> updateConsole(@Valid @RequestBody ModelGovernanceUpdateRequest request) {
        checkAdmin();
        long version = modelManagementService.updateGovernance(request, UserContext.getUserId());
        return Response.success(Map.of("version", version, "status", "updated"));
    }

    @PostMapping("/routes/preview")
    @RateLimiter(key = "model-route-preview", time = 60, count = 5, limitType = LimitType.USER)
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

    private void checkAdmin() {
        String userId = UserContext.getUserId();
        if (!userService.checkAdmin(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Permission denied: Admin access required");
        }
    }
}
