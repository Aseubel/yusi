package com.aseubel.yusi.common.event;

import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.constant.SourceRevision;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Lifecycle event for a card authored by the current user.
 */
@Getter
public class PlazaCardChangedEvent extends ApplicationEvent {

    public enum Type {
        WRITE,
        MODIFY,
        DELETE
    }

    private final CognitionIngestCommand command;
    private final Type type;
    private final String eventId;
    private final Long sourceRevision;

    public PlazaCardChangedEvent(Object source, CognitionIngestCommand command, Type type) {
        super(source);
        this.command = command;
        this.type = type;
        this.eventId = UUID.randomUUID().toString();
        this.sourceRevision = command == null
                ? SourceRevision.INITIAL
                : SourceRevision.initialOrCurrent(command.getSourceRevision());
    }
}
