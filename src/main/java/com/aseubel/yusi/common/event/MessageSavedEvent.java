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
    private final String runId;

    public MessageSavedEvent(Object source, String memoryId) {
        this(source, memoryId, null);
    }

    public MessageSavedEvent(Object source, String memoryId, String runId) {
        super(source);
        this.memoryId = memoryId;
        this.runId = runId;
    }
}
