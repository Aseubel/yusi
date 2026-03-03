package com.aseubel.yusi.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PromptUpdatedEvent extends ApplicationEvent {
    private final String promptName;

    public PromptUpdatedEvent(Object source, String promptName) {
        super(source);
        this.promptName = promptName;
    }
}
