package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
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
@CrossOrigin("*")
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
        List<UserLocation> locations;
        if (locationType != null && !locationType.isEmpty()) {
            locations = userLocationService.getUserLocationsByType(userId, locationType);
        } else {
            locations = userLocationService.getUserLocations(userId);
        }
        return Response.success(locations);
    }

    /**
     * 添加新地点
     */
    @PostMapping
    public Response<UserLocation> addLocation(@RequestBody AddLocationRequest request) {
        UserLocation location = userLocationService.addLocation(request);
        return Response.success(location);
    }

    /**
     * 更新地点
     */
    @PutMapping
    public Response<UserLocation> updateLocation(@RequestBody UpdateLocationRequest request) {
        UserLocation location = userLocationService.updateLocation(request);
        return Response.success(location);
    }

    /**
     * 删除地点
     */
    @DeleteMapping("/{locationId}")
    public Response<?> deleteLocation(
            @RequestParam String userId,
            @PathVariable String locationId) {
        userLocationService.deleteLocation(userId, locationId);
        return Response.success();
    }
}
