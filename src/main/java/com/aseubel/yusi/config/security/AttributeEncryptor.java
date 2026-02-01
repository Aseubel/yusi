package com.aseubel.yusi.config.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Converter
public class AttributeEncryptor implements AttributeConverter<String, String> {

    // 对称加密算法类型
    private static final String ALG = "AES";
    // GCM 模式同时提供机密性与完整性校验
    private static final String TRANS = "AES/GCM/NoPadding";
    // GCM 认证标签长度（bit）
    private static final int GCM_TAG_LENGTH = 128;

    private SecretKey key() {
        // 测试环境可用 JVM 属性覆盖，生产优先使用环境变量
        String k = System.getProperty("YUSI_ENCRYPTION_KEY");
        if (k == null || k.isEmpty()) {
            k = System.getenv("YUSI_ENCRYPTION_KEY");
        }
        if (k == null || k.length() < 16) {
            throw new IllegalStateException("YUSI_ENCRYPTION_KEY must be set and at least 16 characters long.");
        }
        // 仅取前 16 字节作为 AES-128 密钥
        byte[] bytes = k.substring(0, 16).getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(bytes, ALG);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null)
            return null;
        try {
            SecretKey key = key();
            // 为每次加密生成随机 IV，避免重复导致的安全问题
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANS);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            // 组合为 [iv长度|iv|密文] 并用 Base64 持久化
            ByteBuffer bb = ByteBuffer.allocate(4 + iv.length + cipherText.length);
            bb.putInt(iv.length);
            bb.put(iv);
            bb.put(cipherText);
            return Base64.getEncoder().encodeToString(bb.array());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt data", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null)
            return null;
        try {
            SecretKey key = key();
            // 从 Base64 解码后按 [iv长度|iv|密文] 解析
            byte[] all = Base64.getDecoder().decode(dbData);
            ByteBuffer bb = ByteBuffer.wrap(all);
            int ivLen = bb.getInt();
            byte[] iv = new byte[ivLen];
            bb.get(iv);
            byte[] cipherText = new byte[bb.remaining()];
            bb.get(cipherText);
            Cipher cipher = Cipher.getInstance(TRANS);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            return dbData;
        }
    }
}
