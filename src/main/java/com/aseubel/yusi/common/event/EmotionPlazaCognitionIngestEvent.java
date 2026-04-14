package com.aseubel.yusi.common.event;

import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class EmotionPlazaCognitionIngestEvent extends ApplicationEvent {

    private final CognitionIngestCommand command;

    public EmotionPlazaCognitionIngestEvent(Object source, CognitionIngestCommand command) {
        super(source);
        this.command = command;
    }
}
