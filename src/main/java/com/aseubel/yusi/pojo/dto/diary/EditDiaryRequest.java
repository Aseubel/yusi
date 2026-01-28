package com.aseubel.yusi.pojo.dto.diary;

import com.aseubel.yusi.pojo.entity.Diary;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * @author Aseubel
 * @date 2025/5/7 上午10:11
 */
@Data
public class EditDiaryRequest implements Serializable {

    private String userId;

    private String diaryId;

    private String title;

    /**
     * 日记内容（可能是密文或明文，取决于 clientEncrypted）
     */
    private String content;

    /**
     * 明文内容，仅用于 RAG 向量化（不持久化存储）
     */
    private String plainContent;

    private Boolean visibility;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer.class)
    @JsonDeserialize(using = LocalDateDeserializer.class)
    private LocalDate entryDate;

    /**
     * 标识内容是否由客户端加密
     */
    private Boolean clientEncrypted = true;

    // ========== Geo-location fields (Epic 5) ==========
    private Double latitude;
    private Double longitude;
    private String address;
    private String placeName;
    private String placeId;

    public Diary toDiary() {
        return Diary.builder()
                .userId(userId)
                .diaryId(diaryId)
                .title(title)
                .content(content)
                .plainContent(plainContent)
                .visibility(visibility)
                .entryDate(entryDate)
                .clientEncrypted(clientEncrypted)
                .latitude(latitude)
                .longitude(longitude)
                .address(address)
                .placeName(placeName)
                .placeId(placeId)
                .build();
    }
}
