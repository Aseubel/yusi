package com.aseubel.yusi.pojo.dto.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 用户可见的关系图谱实体摘要，不包含 mention 原文。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LifeGraphMemoryItem {

    private Long id;
    private String type;
    private String displayName;
    private String summary;
    private Integer mentionCount;
    private Integer relationCount;
    private Double confidence;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime validUntil;
    private Boolean matchAllowed;
    private Boolean hidden;
    private String lifecycleStatus;
    private List<LifeGraphSourceItem> sources;
}
