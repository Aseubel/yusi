package com.aseubel.yusi.service.location;

import com.aseubel.yusi.pojo.dto.location.AddLocationRequest;
import com.aseubel.yusi.pojo.dto.location.UpdateLocationRequest;
import com.aseubel.yusi.pojo.entity.UserLocation;

import java.util.List;

/**
 * 用户地点服务接口
 * Epic 5: 时空足迹
 *
 * @author Aseubel
 * @date 2026/1/28
 */
public interface UserLocationService {

    /**
     * 获取用户所有保存的地点
     */
    List<UserLocation> getUserLocations(String userId);

    /**
     * 按类型获取用户地点
     */
    List<UserLocation> getUserLocationsByType(String userId, String locationType);

    /**
     * 添加新地点
     */
    UserLocation addLocation(AddLocationRequest request);

    /**
     * 更新地点
     */
    UserLocation updateLocation(UpdateLocationRequest request);

    /**
     * 删除地点
     */
    void deleteLocation(String userId, String locationId);
}
