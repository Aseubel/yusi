package com.aseubel.yusi.pojo.dto.soulplaza;

import lombok.Data;

/**
 * 发送共鸣信号请求 DTO（F9.2）。
 *
 * @author Aseubel
 * @date 2026/06/03
 */
@Data
public class SendSignalRequest {

    /** 接收方用户ID */
    private String toUserId;

    /** 触发共鸣的广场帖子ID（可选） */
    private Long cardId;

    /** 附言（可选，不超过100字） */
    private String message;
}
