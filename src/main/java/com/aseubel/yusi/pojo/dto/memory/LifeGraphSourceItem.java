package com.aseubel.yusi.pojo.dto.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 关系图谱的安全来源引用，只保留业务 ID 和时间元数据。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LifeGraphSourceItem {

    private String sourceId;
    private String sourceType;
    private LocalDate entryDate;
    private LocalDateTime createdAt;
}
