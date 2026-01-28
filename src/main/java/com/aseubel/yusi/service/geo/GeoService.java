package com.aseubel.yusi.service.geo;

import com.aseubel.yusi.pojo.dto.geo.POIResult;
import com.aseubel.yusi.pojo.dto.geo.ReverseGeocodeResult;

import java.util.List;

public interface GeoService {

    /**
     * POI 搜索（输入提示/自动补全）
     */
    List<POIResult> searchPOI(String keyword, String city);

    /**
     * 逆地理编码：经纬度 -> 地址
     */
    ReverseGeocodeResult reverseGeocode(Double latitude, Double longitude);
}
