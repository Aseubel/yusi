package com.aseubel.yusi.pojo.entity;

import com.aseubel.yusi.common.utils.UuidUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

/**
 * 用户保存的地点（常用地点/重要地点）
 * Epic 5: 时空足迹
 * 
 * @author Aseubel
 * @date 2026/1/28
 */
@Data
@Entity
@Builder
@ToString
@Table(name = "user_location")
@DynamicInsert
@DynamicUpdate
@AllArgsConstructor
@NoArgsConstructor
public class UserLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "location_id")
    private String locationId;

    @Column(name = "user_id")
    private String userId;

    /**
     * 用户自定义名称（如：家、公司、初恋的咖啡馆）
     */
    @Column(name = "name")
    private String name;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    /**
     * 详细地址
     */
    @Column(name = "address")
    private String address;

    /**
     * 地图 POI ID
     */
    @Column(name = "place_id")
    private String placeId;

    /**
     * 地点类型：FREQUENT（常用地点）/ IMPORTANT（重要地点）
     */
    @Column(name = "location_type")
    private String locationType;

    /**
     * 图标标识（如：home, work, heart, star）
     */
    @Column(name = "icon")
    private String icon;

    @Column(name = "create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime updateTime;

    public String generateId() {
        this.locationId = UuidUtils.genUuidSimple();
        return this.locationId;
    }
}
