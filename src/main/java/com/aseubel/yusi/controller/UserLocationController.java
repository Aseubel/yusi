package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.exception.AuthorizationException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.common.ratelimit.LimitType;
import com.aseubel.yusi.common.ratelimit.RateLimiter;
import com.aseubel.yusi.pojo.dto.location.AddLocationRequest;
import com.aseubel.yusi.pojo.dto.location.UpdateLocationRequest;
import com.aseubel.yusi.pojo.entity.UserLocation;
import com.aseubel.yusi.service.location.UserLocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户地点管理控制器
 * Epic 5: 时空足迹
 *
 * @author Aseubel
 * @date 2026/1/28
 */
@Auth
@Slf4j
@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class UserLocationController {

    private final UserLocationService userLocationService;

    /**
     * 获取用户所有保存的地点
     */
    @GetMapping("/list")
    public Response<List<UserLocation>> getUserLocations(
            @RequestParam String userId,
            @RequestParam(required = false) String locationType) {
        requireCurrentUser(userId);
        List<UserLocation> locations;
        if (locationType != null && !locationType.isEmpty()) {
            locations = userLocationService.getUserLocationsByType(UserContext.getUserId(), locationType);
        } else {
            locations = userLocationService.getUserLocations(UserContext.getUserId());
        }
        return Response.success(locations);
    }

    /**
     * 添加新地点
     */
    @PostMapping
    @RateLimiter(key = "location-create", time = 60, count = 20, limitType = LimitType.USER)
    public Response<UserLocation> addLocation(@RequestBody AddLocationRequest request) {
        request.setUserId(UserContext.getUserId());
        UserLocation location = userLocationService.addLocation(request);
        return Response.success(location);
    }

    /**
     * 更新地点
     */
    @PutMapping
    @RateLimiter(key = "location-update", time = 60, count = 20, limitType = LimitType.USER)
    public Response<UserLocation> updateLocation(@RequestBody UpdateLocationRequest request) {
        request.setUserId(UserContext.getUserId());
        UserLocation location = userLocationService.updateLocation(request);
        return Response.success(location);
    }

    /**
     * 删除地点
     */
    @DeleteMapping("/{locationId}")
    @RateLimiter(key = "location-delete", time = 60, count = 20, limitType = LimitType.USER)
    public Response<?> deleteLocation(
            @RequestParam String userId,
            @PathVariable String locationId) {
        requireCurrentUser(userId);
        userLocationService.deleteLocation(UserContext.getUserId(), locationId);
        return Response.success();
    }

    private void requireCurrentUser(String userId) {
        if (!UserContext.getUserId().equals(userId)) {
            throw new AuthorizationException(ErrorCode.FORBIDDEN, "无权访问其他用户的地点");
        }
    }
}
