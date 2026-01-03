package com.aseubel.yusi.pojo.dto.chat;

import lombok.Data;

/**
 * @author Aseubel
 * @date 2026/1/3 下午10:50
 */
@Data
public class SendMessageRequest {
    private Long matchId;
    private String content;
}
