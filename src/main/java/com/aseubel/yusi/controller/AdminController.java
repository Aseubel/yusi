package com.aseubel.yusi.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.service.user.UserService;
import com.aseubel.yusi.service.user.AdminService;
import com.aseubel.yusi.pojo.dto.admin.AdminStatsResponse;
import com.aseubel.yusi.pojo.dto.admin.AdminPermissionResponse;
import com.aseubel.yusi.pojo.dto.admin.AdminUserResponse;
import com.aseubel.yusi.pojo.dto.admin.ScenarioAuditRequest;
import com.aseubel.yusi.pojo.dto.admin.SecurityAuditEventResponse;
import com.aseubel.yusi.pojo.dto.admin.SecurityAuditQuery;
import com.aseubel.yusi.pojo.constant.SecurityAuditAction;
import com.aseubel.yusi.pojo.constant.SecurityAuditDetailKeys;
import com.aseubel.yusi.pojo.constant.SecurityAuditOperation;
import com.aseubel.yusi.pojo.constant.SecurityAuditOutcome;
import com.aseubel.yusi.pojo.constant.SecurityAuditReasonCode;
import com.aseubel.yusi.pojo.constant.SecurityAuditResourceType;
import com.aseubel.yusi.pojo.dto.notification.AnnouncementResponse;
import com.aseubel.yusi.pojo.dto.notification.PublishAnnouncementRequest;
import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.ratelimit.LimitType;
import com.aseubel.yusi.common.ratelimit.RateLimiter;
import com.aseubel.yusi.pojo.entity.SituationScenario;
import com.aseubel.yusi.pojo.entity.Suggestion;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.service.ai.embedding.EmbeddingBatchService;
import com.aseubel.yusi.service.notification.NotificationService;
import com.aseubel.yusi.service.suggestion.SuggestionService;
import com.aseubel.yusi.service.security.SecurityAuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import com.aseubel.yusi.config.MemoryConfigProperties;
import groovy.util.logging.Slf4j;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Auth
@Slf4j
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService userService;

    private final AdminService adminService;
    private final SuggestionService suggestionService;
    private final EmbeddingBatchService embeddingBatchService;
    private final MemoryConfigProperties memoryConfigProperties;
    private final NotificationService notificationService;
    private final SecurityAuditService securityAuditService;

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

    private void checkSuperAdminPermission() {
        String userId = UserContext.getUserId();
        User user = userService.getUserByUserId(userId);
        int permissionLevel = user != null && user.getPermissionLevel() != null ? user.getPermissionLevel() : 0;
        if (permissionLevel < 99) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Permission denied: Super admin access required (level >= 99)");
        }
    }

    @GetMapping("/stats")
    public Response<AdminStatsResponse> getStats() {
        checkAdminPermission();
        return Response.success(adminService.getStats());
    }

    @GetMapping("/users")
    public Response<Page<AdminUserResponse>> getUsers(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        checkAdminPermission();
        return Response.success(adminService.getUsers(PageRequest.of(page, size), search));
    }

    @GetMapping("/me")
    public Response<AdminPermissionResponse> getCurrentAdminPermission() {
        checkAdminPermission();
        return Response.success(new AdminPermissionResponse(getCurrentUserPermissionLevel()));
    }

    @GetMapping("/audit")
    public Response<Page<SecurityAuditEventResponse>> getSecurityAudit(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) com.aseubel.yusi.pojo.constant.SecurityAuditAction action,
            @RequestParam(required = false) com.aseubel.yusi.pojo.constant.SecurityAuditOutcome outcome,
            @RequestParam(required = false) com.aseubel.yusi.pojo.constant.SecurityAuditResourceType resourceType,
            @RequestParam(required = false) String userId) {
        checkAdminPermission();
        return Response.success(securityAuditService.findAdminPage(true,
                SecurityAuditQuery.builder()
                        .action(action)
                        .outcome(outcome)
                        .resourceType(resourceType)
                        .userId(userId)
                        .build(),
                PageRequest.of(page, size)));
    }

    @PostMapping("/users/{userId}/permission")
    @RateLimiter(key = "admin-user-permission", time = 60, count = 10, limitType = LimitType.USER)
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

    @GetMapping("/scenarios")
    public Response<Page<SituationScenario>> getAllScenarios(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status) {
        checkAdminPermission();
        return Response.success(adminService.getAllScenarios(PageRequest.of(page, size), status));
    }

    @PostMapping("/scenarios/{scenarioId}/audit")
    @RateLimiter(key = "admin-scenario-audit", time = 60, count = 20, limitType = LimitType.USER)
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
    @RateLimiter(key = "admin-suggestion-reply", time = 60, count = 10, limitType = LimitType.USER)
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
    @RateLimiter(key = "admin-suggestion-status", time = 60, count = 20, limitType = LimitType.USER)
    public Response<Void> updateSuggestionStatus(@PathVariable String suggestionId,
            @RequestBody Map<String, String> payload) {
        checkAdminPermission();
        String status = payload.get("status");
        if (status == null || status.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Status is required");
        }
        String repliedBy = UserContext.getUserId();
        String repliedAtStr = payload.get("repliedAt");
        LocalDateTime repliedAt = null;
        if (repliedAtStr != null && !repliedAtStr.isEmpty()) {
            try {
                repliedAt = OffsetDateTime.parse(repliedAtStr).toLocalDateTime();
            } catch (Exception e) {
                repliedAt = LocalDateTime.parse(repliedAtStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        }
        suggestionService.updateStatus(suggestionId, status, repliedBy, repliedAt);
        return Response.success();
    }

    @GetMapping("/suggestions/pending-count")
    public Response<Long> getPendingSuggestionCount() {
        checkAdminPermission();
        return Response.success(suggestionService.getPendingCount());
    }

    @GetMapping("/announcements")
    public Response<Page<AnnouncementResponse>> getAnnouncements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        checkAdminPermission();
        return Response.success(notificationService.getAnnouncements(page, size));
    }

    @PostMapping("/announcements")
    @RateLimiter(key = "admin-announcement-publish", time = 60, count = 5, limitType = LimitType.USER)
    public Response<AnnouncementResponse> publishAnnouncement(
            @Valid @RequestBody PublishAnnouncementRequest request) {
        checkAdminPermission();
        return Response.success(notificationService.publishAnnouncement(request, UserContext.getUserId()));
    }

    @PostMapping("/embeddings/full-sync")
    @RateLimiter(key = "admin-embeddings-full-sync", time = 3600, count = 1, limitType = LimitType.USER)
    public Response<Integer> fullSyncEmbeddings() {
        checkSuperAdminPermission();
        int count = embeddingBatchService.fullSync();
        securityAuditService.recordAdmin(SecurityAuditAction.EMBEDDINGS_FULL_SYNC, UserContext.getUserId(), null,
                SecurityAuditResourceType.EMBEDDING_SYNC, "all", SecurityAuditOutcome.SUCCESS,
                SecurityAuditReasonCode.ADMIN_MUTATION,
                Map.of(SecurityAuditDetailKeys.OPERATION, SecurityAuditOperation.FULL_SYNC.name(),
                        SecurityAuditDetailKeys.COUNT, String.valueOf(count)));
        return Response.success(count);
    }

    @PostMapping("/users/{userId}/deregister")
    @RateLimiter(key = "admin-user-deregister", time = 600, count = 2, limitType = LimitType.USER)
    public Response<Void> deregisterUser(@PathVariable String userId) {
        checkSuperAdminPermission();
        adminService.deregisterUser(userId);
        return Response.success();
    }

    /**
     * 获取记忆系统配置信息
     */
    @GetMapping("/memory/config")
    public Response<Map<String, Object>> getMemoryConfig() {
        checkAdminPermission();
        Map<String, Object> config = new HashMap<>();
        config.put("contextWindowSize", memoryConfigProperties.getContextWindowSize());
        config.put("midTermSummaryInterval", memoryConfigProperties.getMidTermSummaryInterval());
        config.put("midTermSummaryIntervalMinutes", memoryConfigProperties.getMidTermSummaryInterval() / 1000 / 60);
        config.put("midTermScanCron", memoryConfigProperties.getMidTermScanCron());
        return Response.success(config);
    }
}
