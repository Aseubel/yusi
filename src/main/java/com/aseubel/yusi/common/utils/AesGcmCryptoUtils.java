package com.aseubel.yusi.common.utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class AesGcmCryptoUtils {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 100000;
    private static final int PBKDF2_KEY_LENGTH_BITS = 256;

    /**
     * ThreadLocal SecureRandom with non-blocking algorithm.
     * - 使用 DRBG (Deterministic Random Bit Generator) 算法，符合 NIST SP 800-90A 标准
     * - ThreadLocal 避免线程竞争，同时复用实例
     * - DRBG 不依赖系统熵池，不会在 Linux /dev/random 下阻塞
     * - 在高并发加密场景下性能显著提升
     * 
     * 部署注意：Linux 环境可额外配置 JVM 参数 -Djava.security.egd=file:/dev/./urandom
     */
    private static final ThreadLocal<SecureRandom> SECURE_RANDOM = ThreadLocal.withInitial(() -> {
        try {
            // 优先使用 DRBG（Java 9+，非阻塞，符合 NIST 标准）
            return SecureRandom.getInstance("DRBG");
        } catch (NoSuchAlgorithmException e) {
            try {
                // 降级：SHA1PRNG（性能较好，兼容性强）
                return SecureRandom.getInstance("SHA1PRNG");
            } catch (NoSuchAlgorithmException ex) {
                // 最终降级：平台默认实现
                return new SecureRandom();
            }
        }
    });

    private AesGcmCryptoUtils() {
    }

    public static String encryptText(String plaintext, String keyBase64) {
        if (plaintext == null) {
            return null;
        }
        return encryptText(plaintext, decodeKeyBase64(keyBase64));
    }

    public static String decryptText(String encryptedBase64, String keyBase64) {
        if (encryptedBase64 == null) {
            return null;
        }
        return decryptText(encryptedBase64, decodeKeyBase64(keyBase64));
    }

    public static byte[] deriveKeyFromPassword(String password, String saltBase64) {
        if (password == null) {
            throw new IllegalStateException("Password is null");
        }
        if (saltBase64 == null || saltBase64.isBlank()) {
            throw new IllegalStateException("Salt is blank");
        }

        byte[] salt;
        try {
            salt = Base64.getDecoder().decode(saltBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid Base64 salt", e);
        }

        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 key derivation failed", e);
        }
    }

    public static String encryptText(String plaintext, byte[] keyBytes) {
        if (plaintext == null) {
            return null;
        }
        validateAesKey(keyBytes);

        byte[] iv = new byte[IV_LENGTH_BYTES];
        SECURE_RANDOM.get().nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    public static String decryptText(String encryptedBase64, byte[] keyBytes) {
        if (encryptedBase64 == null) {
            return null;
        }
        validateAesKey(keyBytes);

        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(encryptedBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid Base64 ciphertext", e);
        }

        if (combined.length <= IV_LENGTH_BYTES) {
            throw new IllegalStateException("Ciphertext too short");
        }

        byte[] iv = new byte[IV_LENGTH_BYTES];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);

        byte[] cipherText = new byte[combined.length - IV_LENGTH_BYTES];
        System.arraycopy(combined, IV_LENGTH_BYTES, cipherText, 0, cipherText.length);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
    }

    private static byte[] decodeKeyBase64(String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new IllegalStateException("Key is blank");
        }
        try {
            return Base64.getDecoder().decode(keyBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid Base64 key", e);
        }
    }

    private static void validateAesKey(byte[] keyBytes) {
        if (keyBytes == null) {
            throw new IllegalStateException("Key is null");
        }
        int len = keyBytes.length;
        if (len != 16 && len != 24 && len != 32) {
            throw new IllegalStateException("Invalid AES key length: " + len);
        }
    }
}
