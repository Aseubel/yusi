package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.pojo.dto.developer.DeveloperConfigVO;
import com.aseubel.yusi.pojo.dto.developer.DeveloperScopeUpdateRequest;
import com.aseubel.yusi.service.developer.DeveloperConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@Auth
@RestController
@CrossOrigin("*")
@RequestMapping("/api/developer/config")
@RequiredArgsConstructor
public class DeveloperConfigController {

    private final DeveloperConfigService developerConfigService;

    @GetMapping
    public Response<DeveloperConfigVO> getConfig() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            return Response.success(null); // Auth interceptor should block, but just in case
        }
        return Response.success(developerConfigService.getConfig(userId));
    }

    @PostMapping("/api-key")
    public Response<DeveloperConfigVO> rotateApiKey() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            return Response.success(null);
        }
        return Response.success(developerConfigService.rotateApiKey(userId));
    }

    @PutMapping("/api-key/scopes")
    public Response<DeveloperConfigVO> updateScopes(@RequestBody DeveloperScopeUpdateRequest request) {
        return Response.success(developerConfigService.updateScopes(
                UserContext.getUserId(), request.getScopes()));
    }

    @DeleteMapping("/api-key")
    public Response<Void> revokeApiKey() {
        developerConfigService.revokeApiKey(UserContext.getUserId());
        return Response.success();
    }
}
