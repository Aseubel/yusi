package com.aseubel.yusi.pojo.dto.developer;

import lombok.Data;

import java.util.List;

@Data
public class DeveloperScopeUpdateRequest {
    private List<String> scopes;
}
