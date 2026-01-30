package com.aseubel.yusi.pojo.dto.diary;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 日记足迹点 DTO - 用于足迹地图展示
 */
public class DiaryFootprint implements Serializable {

    private String diaryId;
    private Double latitude;
    private Double longitude;
    private String placeName;
    private String address;
    private LocalDateTime createTime;
    private String emotion; // 情绪标签（来自 AI 分析）

    public DiaryFootprint() {
    }

    public DiaryFootprint(String diaryId, Double latitude, Double longitude,
            String placeName, String address, LocalDateTime createTime, String emotion) {
        this.diaryId = diaryId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.placeName = placeName;
        this.address = address;
        this.createTime = createTime;
        this.emotion = emotion;
    }

    public String getDiaryId() {
        return diaryId;
    }

    public void setDiaryId(String diaryId) {
        this.diaryId = diaryId;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getPlaceName() {
        return placeName;
    }

    public void setPlaceName(String placeName) {
        this.placeName = placeName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getEmotion() {
        return emotion;
    }

    public void setEmotion(String emotion) {
        this.emotion = emotion;
    }
}
