package com.aseubel.yusi.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.service.user.UserService;
import com.aseubel.yusi.service.user.AdminService;
import com.aseubel.yusi.pojo.dto.admin.AdminStatsResponse;
import com.aseubel.yusi.pojo.dto.admin.ScenarioAuditRequest;
import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.pojo.entity.SituationScenario;
import com.aseubel.yusi.pojo.entity.User;
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

    private void checkAdminPermission() {
        String userId = UserContext.getUserId();
        if (!userService.checkAdmin(userId)) {
            throw new BusinessException("Permission denied: Admin access required");
        }
    }

    // @PostMapping("/remove-diary-collection")
    // public void removeDiaryCollection() {
    // checkAdminPermission();
    // milvusEmbeddingStore.removeAll();
    // }

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
        Integer level = payload.get("level");
        if (level == null)
            throw new BusinessException("Level is required");
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
}
