package com.aseubel.yusi.pojo.dto.prompt;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PromptUpdateRequest {
    @Size(max = 255)
    private String name;

    private String template;

    @Size(max = 64)
    private String version;

    private Boolean active;

    @Size(max = 64)
    private String scope;

    @Size(max = 16)
    private String locale;

    @Size(max = 500)
    private String description;

    @Size(max = 255)
    private String tags;

    @JsonProperty("isDefault")
    private Boolean isDefault;

    @Min(0)
    @Max(10000)
    private Integer priority;
}
