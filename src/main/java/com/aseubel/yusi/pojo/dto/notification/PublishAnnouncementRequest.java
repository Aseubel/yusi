package com.aseubel.yusi.pojo.dto.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Input for an administrator announcement publication. */
@Data
public class PublishAnnouncementRequest {

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 120, message = "公告标题不能超过120个字符")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    @Size(max = 5000, message = "公告内容不能超过5000个字符")
    private String content;

    @Size(max = 32, message = "公告受众类型不合法")
    private String audience = "ALL";
}
