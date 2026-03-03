package com.aseubel.yusi.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 消息保存事件
 * 当 AI 回复写入数据库后发布，用于事件驱动触发中期记忆压缩
 *
 * @author Aseubel
 * @date 2026/03/03
 */
@Getter
public class MessageSavedEvent extends ApplicationEvent {

    /**
     * 记忆 ID（通常为用户 ID）
     */
    private final String memoryId;

    public MessageSavedEvent(Object source, String memoryId) {
        super(source);
        this.memoryId = memoryId;
    }
}
