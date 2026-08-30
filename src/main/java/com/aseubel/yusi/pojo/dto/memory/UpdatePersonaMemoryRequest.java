package com.aseubel.yusi.pojo.dto.memory;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 稳定画像的用户微调请求。未提供的字段保持原值。 */
@Data
public class UpdatePersonaMemoryRequest {

    @Size(max = 50, message = "称呼不能超过50个字符")
    private String preferredName;

    @Size(max = 100, message = "所在地不能超过100个字符")
    private String location;

    @Size(max = 500, message = "兴趣偏好不能超过500个字符")
    private String interests;

    @Size(max = 200, message = "语气偏好不能超过200个字符")
    private String tone;

    @Size(max = 4000, message = "相处方式不能超过4000个字符")
    private String customInstructions;

    @DecimalMin(value = "0.0", message = "置信度不能小于0")
    @DecimalMax(value = "1.0", message = "置信度不能大于1")
    private Double confidence;

    private Boolean matchAllowed;
    private Boolean hidden;

    /** 要清空的 Persona 内容字段名。 */
    private List<String> clearFields;
}
