package com.aseubel.yusi.pojo.dto.prompt;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.aseubel.yusi.pojo.entity.PromptTemplate;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PromptResponse {
    private Long id;
    private String name;
    private String template;
    private String version;
    private Boolean active;
    private String scope;
    private String locale;
    private String description;
    private String tags;
    @JsonProperty("isDefault")
    private Boolean isDefault;
    private Integer priority;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PromptResponse from(PromptTemplate entity) {
        PromptResponse response = new PromptResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setTemplate(entity.getTemplate());
        response.setVersion(entity.getVersion());
        response.setActive(entity.getActive());
        response.setScope(entity.getScope());
        response.setLocale(entity.getLocale());
        response.setDescription(entity.getDescription());
        response.setTags(entity.getTags());
        response.setIsDefault(entity.getIsDefault());
        response.setPriority(entity.getPriority());
        response.setUpdatedBy(entity.getUpdatedBy());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
