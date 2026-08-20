package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.ratelimit.LimitType;
import com.aseubel.yusi.common.ratelimit.RateLimiter;
import com.aseubel.yusi.pojo.dto.memory.MemoryCenterItem;
import com.aseubel.yusi.pojo.dto.memory.MemoryCenterResponse;
import com.aseubel.yusi.pojo.dto.memory.LifeGraphMemoryItem;
import com.aseubel.yusi.pojo.dto.memory.LifeGraphMemoryResponse;
import com.aseubel.yusi.pojo.dto.memory.PersonaMemoryItem;
import com.aseubel.yusi.pojo.dto.memory.UpdateMemoryRequest;
import com.aseubel.yusi.pojo.dto.memory.UpdateLifeGraphMemoryRequest;
import com.aseubel.yusi.pojo.dto.memory.UpdatePersonaMemoryRequest;
import com.aseubel.yusi.service.memory.LifeGraphLifecycleService;
import com.aseubel.yusi.service.memory.MidTermMemoryLifecycleService;
import com.aseubel.yusi.service.memory.UserPersonaLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Auth
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/memory")
public class MemoryCenterController {

    private final MidTermMemoryLifecycleService lifecycleService;
    private final UserPersonaLifecycleService personaLifecycleService;
    private final LifeGraphLifecycleService lifeGraphLifecycleService;

    @GetMapping("/center")
    public Response<MemoryCenterResponse> getCenter(
            @RequestParam(defaultValue = "50") int limit) {
        if (limit < 1 || limit > 100) {
            return Response.fail("记忆数量参数不合法");
        }
        return Response.success(lifecycleService.list(UserContext.getUserId(), limit));
    }

    @PatchMapping("/center/{id}")
    @RateLimiter(key = "memory-center-update", time = 60, count = 20, limitType = LimitType.USER)
    public Response<MemoryCenterItem> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMemoryRequest request) {
        return Response.success(lifecycleService.update(UserContext.getUserId(), id, request));
    }

    @DeleteMapping("/center/{id}")
    @RateLimiter(key = "memory-center-delete", time = 60, count = 20, limitType = LimitType.USER)
    public Response<Void> delete(@PathVariable Long id) {
        lifecycleService.delete(UserContext.getUserId(), id);
        return Response.success();
    }

    @GetMapping("/persona")
    public Response<PersonaMemoryItem> getPersona() {
        return Response.success(personaLifecycleService.get(UserContext.getUserId()));
    }

    @PatchMapping("/persona")
    @RateLimiter(key = "memory-persona-update", time = 60, count = 20, limitType = LimitType.USER)
    public Response<PersonaMemoryItem> updatePersona(
            @Valid @RequestBody UpdatePersonaMemoryRequest request) {
        return Response.success(personaLifecycleService.update(UserContext.getUserId(), request));
    }

    @DeleteMapping("/persona")
    @RateLimiter(key = "memory-persona-delete", time = 60, count = 20, limitType = LimitType.USER)
    public Response<Void> deletePersona() {
        personaLifecycleService.delete(UserContext.getUserId());
        return Response.success();
    }

    @GetMapping("/life-graph")
    public Response<LifeGraphMemoryResponse> getLifeGraph(
            @RequestParam(defaultValue = "50") int limit) {
        return Response.success(lifeGraphLifecycleService.list(UserContext.getUserId(), limit));
    }

    @PatchMapping("/life-graph/{id}")
    @RateLimiter(key = "memory-life-graph-update", time = 60, count = 20, limitType = LimitType.USER)
    public Response<LifeGraphMemoryItem> updateLifeGraph(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLifeGraphMemoryRequest request) {
        return Response.success(lifeGraphLifecycleService.update(UserContext.getUserId(), id, request));
    }

    @DeleteMapping("/life-graph/{id}")
    @RateLimiter(key = "memory-life-graph-delete", time = 60, count = 20, limitType = LimitType.USER)
    public Response<Void> deleteLifeGraph(@PathVariable Long id) {
        lifeGraphLifecycleService.delete(UserContext.getUserId(), id);
        return Response.success();
    }
}
