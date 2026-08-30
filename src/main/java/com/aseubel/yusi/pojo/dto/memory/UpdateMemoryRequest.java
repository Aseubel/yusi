package com.aseubel.yusi.pojo.dto.memory;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 记忆中心的用户微调请求。未提供的字段保持原值。 */
@Data
public class UpdateMemoryRequest {

    @Size(max = 2000, message = "记忆摘要不能超过2000个字符")
    private String summary;

    @DecimalMin(value = "0.0", message = "置信度不能小于0")
    @DecimalMax(value = "1.0", message = "置信度不能大于1")
    private Double confidence;

    private Boolean matchAllowed;
    private Boolean hidden;
}
