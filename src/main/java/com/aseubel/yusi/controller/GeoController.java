package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.pojo.dto.geo.POIResult;
import com.aseubel.yusi.pojo.dto.geo.ReverseGeocodeResult;
import com.aseubel.yusi.service.geo.GeoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 地理位置代理 API
 * 前端通过此接口调用高德地图服务，避免 API Key 暴露在前端
 */
@RestController
@CrossOrigin("*")
@RequestMapping("/api/geo")
public class GeoController {

    @Autowired
    private GeoService geoService;

    /**
     * POI 搜索 / 输入提示
     */
    @GetMapping("/search")
    public Response<List<POIResult>> searchPOI(
            @RequestParam String keyword,
            @RequestParam(required = false, defaultValue = "") String city) {
        if (keyword == null || keyword.trim().length() < 2) {
            return Response.success(List.of());
        }
        List<POIResult> results = geoService.searchPOI(keyword.trim(), city);
        return Response.success(results);
    }

    /**
     * 逆地理编码
     */
    @GetMapping("/reverse")
    public Response<ReverseGeocodeResult> reverseGeocode(
            @RequestParam Double lat,
            @RequestParam Double lng) {
        if (lat == null || lng == null) {
            return Response.fail("参数无效");
        }
        ReverseGeocodeResult result = geoService.reverseGeocode(lat, lng);
        if (result == null) {
            return Response.fail("地址解析失败");
        }
        return Response.success(result);
    }
}
