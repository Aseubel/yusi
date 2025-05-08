package com.aseubel.yusi.common.disruptor;

import lombok.Getter;

/**
 * @author Aseubel
 * @date 2025/5/7 下午2:02
 */
@Getter
public enum EventType {
    REGISTER,
    LOGIN,
    LOGOUT,
    DIARY_WRITE,
    DIARY_DELETE,
    DIARY_MODIFY
}
