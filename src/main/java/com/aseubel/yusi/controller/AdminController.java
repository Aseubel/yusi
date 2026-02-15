package com.aseubel.yusi.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.service.user.UserService;
import com.aseubel.yusi.service.user.AdminService;
import com.aseubel.yusi.pojo.dto.admin.AdminStatsResponse;
import com.aseubel.yusi.pojo.dto.admin.ScenarioAuditRequest;
import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.pojo.entity.SituationScenario;
import com.aseubel.yusi.pojo.entity.Suggestion;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.service.suggestion.SuggestionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import groovy.util.logging.Slf4j;
import lombok.RequiredArgsConstructor;

@Auth
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@CrossOrigin("*")
public class AdminController {

    private final UserService userService;
    private final MilvusEmbeddingStore milvusEmbeddingStore;

    private final AdminService adminService;
    private final SuggestionService suggestionService;

    private void checkAdminPermission() {
        String userId = UserContext.getUserId();
        if (!userService.checkAdmin(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Permission denied: Admin access required");
        }
    }

    private int getCurrentUserPermissionLevel() {
        String userId = UserContext.getUserId();
        User user = userService.getUserByUserId(userId);
        return user != null && user.getPermissionLevel() != null ? user.getPermissionLevel() : 0;
    }

    @GetMapping("/stats")
    public Response<AdminStatsResponse> getStats() {
        checkAdminPermission();
        return Response.success(adminService.getStats());
    }

    @GetMapping("/users")
    public Response<Page<User>> getUsers(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        checkAdminPermission();
        return Response.success(adminService.getUsers(PageRequest.of(page, size), search));
    }

    @PostMapping("/users/{userId}/permission")
    public Response<Void> updateUserPermission(@PathVariable String userId, @RequestBody Map<String, Integer> payload) {
        checkAdminPermission();
        String currentUserId = UserContext.getUserId();
        Integer level = payload.get("level");
        if (level == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Level is required");
        }
        if (level < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Level must be non-negative");
        }
        
        int currentAdminLevel = getCurrentUserPermissionLevel();
        adminService.validatePermissionChange(currentUserId, userId, level, currentAdminLevel);
        adminService.updateUserPermission(userId, level);
        return Response.success();
    }

    @GetMapping("/scenarios/pending")
    public Response<Page<SituationScenario>> getPendingScenarios(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        checkAdminPermission();
        return Response.success(adminService.getPendingScenarios(PageRequest.of(page, size)));
    }

    @PostMapping("/scenarios/{scenarioId}/audit")
    public Response<Void> auditScenario(@PathVariable String scenarioId, @RequestBody ScenarioAuditRequest request) {
        checkAdminPermission();
        adminService.auditScenario(scenarioId, request);
        return Response.success();
    }

    @GetMapping("/suggestions")
    public Response<Page<Suggestion>> getSuggestions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        checkAdminPermission();
        return Response.success(suggestionService.getAllSuggestions(PageRequest.of(page, size), status));
    }

    @GetMapping("/suggestions/{suggestionId}")
    public Response<Suggestion> getSuggestion(@PathVariable String suggestionId) {
        checkAdminPermission();
        return Response.success(suggestionService.getSuggestion(suggestionId));
    }

    @PostMapping("/suggestions/{suggestionId}/reply")
    public Response<Void> replySuggestion(@PathVariable String suggestionId, @RequestBody Map<String, String> payload) {
        checkAdminPermission();
        String reply = payload.get("reply");
        if (reply == null || reply.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Reply content is required");
        }
        String repliedBy = UserContext.getUserId();
        suggestionService.replySuggestion(suggestionId, reply, repliedBy);
        return Response.success();
    }

    @PostMapping("/suggestions/{suggestionId}/status")
    public Response<Void> updateSuggestionStatus(@PathVariable String suggestionId, @RequestBody Map<String, String> payload) {
        checkAdminPermission();
        String status = payload.get("status");
        if (status == null || status.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Status is required");
        }
        suggestionService.updateStatus(suggestionId, status);
        return Response.success();
    }

    @GetMapping("/suggestions/pending-count")
    public Response<Long> getPendingSuggestionCount() {
        checkAdminPermission();
        return Response.success(suggestionService.getPendingCount());
    }
}
