package com.aseubel.yusi.pojo.dto.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 用户可见的中期记忆摘要，不暴露原始对话内容。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryCenterItem {

    private Long id;
    private String summary;
    private Double importance;
    private Double confidence;
    private String sourceType;
    private String sourceId;
    private String sourceTitle;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime validUntil;
    private Long mergedIntoId;
    private Boolean matchAllowed;
    private Boolean hidden;
    private String lifecycleStatus;
}
