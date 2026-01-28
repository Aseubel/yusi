package com.aseubel.yusi.pojo.dto.location;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新用户地点请求
 * 
 * @author Aseubel
 * @date 2026/1/28
 */
@Data
public class UpdateLocationRequest implements Serializable {

    private String userId;

    private String locationId;

    private String name;

    private Double latitude;

    private Double longitude;

    private String address;

    private String placeId;

    private String locationType;

    private String icon;
}
