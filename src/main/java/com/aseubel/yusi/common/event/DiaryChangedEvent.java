package com.aseubel.yusi.common.event;

import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.constant.SourceRevision;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * 日记变更事件（保存、更新、删除等）
 * 用于替代原 Disruptor 模型进行组件解耦
 */
@Getter
public class DiaryChangedEvent extends ApplicationEvent {

    public enum Type {
        WRITE, MODIFY, READ, DELETE
    }

    private final Diary diary;
    private final Type type;
    private final String eventId;
    private final Long sourceRevision;

    public DiaryChangedEvent(Object source, Diary diary, Type type) {
        this(source, diary, type, UUID.randomUUID().toString());
    }

    public DiaryChangedEvent(Object source, Diary diary, Type type, String eventId) {
        this(source, diary, type, eventId,
                diary == null ? SourceRevision.INITIAL : SourceRevision.initialOrCurrent(diary.getSourceRevision()));
    }

    public DiaryChangedEvent(Object source, Diary diary, Type type, String eventId, Long sourceRevision) {
        super(source);
        this.diary = diary;
        this.type = type;
        this.eventId = eventId == null || eventId.isBlank() ? UUID.randomUUID().toString() : eventId;
        this.sourceRevision = SourceRevision.initialOrCurrent(sourceRevision);
    }
}
