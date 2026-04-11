package com.aseubel.yusi.service.user;

public interface VerificationCodeService {
    /**
     * 生成验证码并发送
     * @param email 邮箱
     * @param bizType 业务类型
     */
    void sendCode(String email, String bizType);

    /**
     * 验证验证码
     * @param email 邮箱
     * @param code 验证码
     * @return 是否验证通过
     */
    boolean verifyCode(String email, String code);
}
