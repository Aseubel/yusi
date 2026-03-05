package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.pojo.dto.model.GroupStrategySwitchRequest;
import com.aseubel.yusi.service.ai.model.ModelManagementService;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import com.aseubel.yusi.service.ai.model.ModelSelectionStrategyType;
import com.aseubel.yusi.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/model")
@CrossOrigin("*")
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
        modelManagementService.switchGroupStrategy(request.getGroup(), request.getStrategy());
        return Response.success(Map.of("group", request.getGroup(), "strategy", request.getStrategy().name()));
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
