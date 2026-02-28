package com.aseubel.yusi.service.user;

public interface VerificationCodeService {
    /**
     * 生成验证码并发送
     * @param email 邮箱
     */
    void sendCode(String email);

    /**
     * 验证验证码
     * @param email 邮箱
     * @param code 验证码
     * @return 是否验证通过
     */
    boolean verifyCode(String email, String code);
}
