package com.aseubel.yusi.service.user.impl;

import cn.hutool.core.util.RandomUtil;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.redis.service.IRedisService;
import com.aseubel.yusi.service.email.EmailService;
import com.aseubel.yusi.service.user.VerificationCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {

    @Autowired
    private IRedisService redisService;

    @Autowired
    private EmailService emailService;

    private static final String KEY_PREFIX = "auth:verification_code:";
    private static final long EXPIRE_MINUTES = 5;

    @Override
    public void sendCode(String email) {
        // 生成6位数字验证码
        String code = RandomUtil.randomNumbers(6);
        String key = KEY_PREFIX + email;

        // 存储到Redis，5分钟过期 (单位毫秒)
        redisService.setValue(key, code, EXPIRE_MINUTES * 60 * 1000);
        
        emailService.sendVerificationCode(email, code);
    }

    @Override
    public boolean verifyCode(String email, String code) {
        String key = KEY_PREFIX + email;
        String storedCode = redisService.getValue(key);
        
        if (storedCode != null && storedCode.equals(code)) {
            // 验证通过后删除验证码，防止重复使用
            redisService.remove(key);
            return true;
        }
        return false;
    }
}
