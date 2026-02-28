package com.aseubel.yusi.service.email.impl;

import com.aliyun.dm20151123.Client;
import com.aliyun.dm20151123.models.SingleSendMailRequest;
import com.aliyun.teaopenapi.models.Config;
import com.aseubel.yusi.config.AliyunDmProperties;
import com.aseubel.yusi.service.email.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AliyunEmailServiceImpl implements EmailService {

    @Autowired
    private AliyunDmProperties properties;

    @Override
    public void sendVerificationCode(String to, String code) {
        if (properties.getAccessKeyId() == null || properties.getAccessKeyId().isEmpty() ||
            properties.getAccessKeySecret() == null || properties.getAccessKeySecret().isEmpty()) {
            log.warn("Aliyun DM credentials are not configured. Skipping email sending. Code: {}", code);
            return;
        }

        try {
            Config config = new Config()
                    .setAccessKeyId(properties.getAccessKeyId())
                    .setAccessKeySecret(properties.getAccessKeySecret());
            config.endpoint = properties.getEndpoint();

            Client client = new Client(config);

            SingleSendMailRequest request = new SingleSendMailRequest()
                    .setAccountName(properties.getAccountName())
                    .setAddressType(1)
                    .setReplyToAddress(false)
                    .setToAddress(to)
                    .setFromAlias(properties.getFromAlias())
                    .setSubject("Yusi 找回密码验证码")
                    .setHtmlBody("<div>您的验证码是：<b>" + code + "</b></div><div>该验证码5分钟内有效，请勿泄露给他人。</div>");

            client.singleSendMail(request);
            log.info("Sent verification code to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}", to, e);
            throw new RuntimeException("发送邮件失败", e);
        }
    }
}
