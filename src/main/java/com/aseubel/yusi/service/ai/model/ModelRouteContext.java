package com.aseubel.yusi.service.ai.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ModelRouteContext {
    String language;
    String scene;
    String group;
}
