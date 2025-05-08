package com.aseubel.yusi.common.disruptor;

import lombok.Data;

/**
 * @author Aseubel
 * @date 2025/5/7 下午1:59
 */
@Data
public class Element {

    private Object data;

    private EventType eventType;

}
