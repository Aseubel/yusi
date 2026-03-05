package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ModelConfigUpdatedEvent extends ApplicationEvent {

    private final ModelRoutingProperties config;

    public ModelConfigUpdatedEvent(Object source, ModelRoutingProperties config) {
        super(source);
        this.config = config;
    }
}
