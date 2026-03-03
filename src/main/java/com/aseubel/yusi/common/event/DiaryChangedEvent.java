package com.aseubel.yusi.common.event;

import com.aseubel.yusi.pojo.entity.Diary;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

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

    public DiaryChangedEvent(Object source, Diary diary, Type type) {
        super(source);
        this.diary = diary;
        this.type = type;
    }
}
