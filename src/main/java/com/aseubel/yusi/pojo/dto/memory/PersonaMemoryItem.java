package com.aseubel.yusi.pojo.dto.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 用户可见的稳定画像摘要，不暴露原始输入内容。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonaMemoryItem {

    private Long id;
    private String preferredName;
    private String location;
    private String interests;
    private String tone;
    private String customInstructions;
    private String sourceType;
    private String sourceId;
    private Double confidence;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean matchAllowed;
    private Boolean hidden;
    private String lifecycleStatus;
}
