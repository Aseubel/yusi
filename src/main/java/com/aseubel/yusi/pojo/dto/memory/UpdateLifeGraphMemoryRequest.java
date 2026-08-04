package com.aseubel.yusi.pojo.dto.memory;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.time.LocalDateTime;

/** 关系图谱实体的用户生命周期微调请求。 */
@Data
public class UpdateLifeGraphMemoryRequest {

    @DecimalMin(value = "0.0", message = "置信度不能小于0")
    @DecimalMax(value = "1.0", message = "置信度不能大于1")
    private Double confidence;

    private Boolean matchAllowed;
    private Boolean hidden;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime validUntil;

    private Boolean clearValidUntil;
}
