package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.pojo.dto.stats.PlatformStatsResponse;
import com.aseubel.yusi.service.stats.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 统计数据控制器
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stats")
@CrossOrigin("*")
public class StatsController {

    private final StatsService statsService;

    /**
     * 获取平台统计数据（公开接口，不需要登录）
     */
    @Auth(required = false)
    @GetMapping("/platform")
    public Response<PlatformStatsResponse> getPlatformStats() {
        return Response.success(statsService.getPlatformStats());
    }
}
