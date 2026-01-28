package com.aseubel.yusi.pojo.dto.location;

import com.aseubel.yusi.pojo.entity.UserLocation;
import lombok.Data;

import java.io.Serializable;

/**
 * 添加用户地点请求
 * 
 * @author Aseubel
 * @date 2026/1/28
 */
@Data
public class AddLocationRequest implements Serializable {

    private String userId;

    private String name;

    private Double latitude;

    private Double longitude;

    private String address;

    private String placeId;

    /**
     * 地点类型：FREQUENT / IMPORTANT
     */
    private String locationType = "FREQUENT";

    /**
     * 图标：home, work, heart, star, etc.
     */
    private String icon = "location";

    public UserLocation toEntity() {
        return UserLocation.builder()
                .userId(userId)
                .name(name)
                .latitude(latitude)
                .longitude(longitude)
                .address(address)
                .placeId(placeId)
                .locationType(locationType)
                .icon(icon)
                .build();
    }
}
