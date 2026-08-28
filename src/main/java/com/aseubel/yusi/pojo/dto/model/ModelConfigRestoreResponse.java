package com.aseubel.yusi.pojo.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 模型配置恢复结果；missingApiKeyModels 为恢复后仍无密钥、需手动补填的模型 ID。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigRestoreResponse {
    private Long version;
    private String action;
    private List<String> missingApiKeyModels;
}
