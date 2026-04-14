package com.aseubel.yusi.common.event;

import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class DiaryCognitionIngestEvent extends ApplicationEvent {

    private final CognitionIngestCommand command;

    public DiaryCognitionIngestEvent(Object source, CognitionIngestCommand command) {
        super(source);
        this.command = command;
    }
}
