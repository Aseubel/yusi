package com.aseubel.yusi.service.email;

public interface EmailService {
    /**
     * 发送验证码邮件
     * @param to 接收者邮箱
     * @param code 验证码
     */
    void sendVerificationCode(String to, String code);
}
