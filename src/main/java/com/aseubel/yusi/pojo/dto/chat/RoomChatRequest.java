package com.aseubel.yusi.pojo.dto.chat;

import lombok.Data;

/**
 * 房间聊天发送消息请求
 */
@Data
public class RoomChatRequest {
    private String roomCode;
    private String content;
}
