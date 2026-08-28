package com.aseubel.yusi.pojo.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 模型配置可恢复历史版本条目（来自 model_config_change_log 成功记录）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigVersionInfo {
    private String changeId;
    private Long version;
    private String operatorId;
    private String action;
    private LocalDateTime createdAt;
}
