package com.aseubel.yusi.config.security;

import cn.hutool.core.util.StrUtil;

import javax.crypto.Cipher;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class CryptoService {

    private final byte[] serverAesKeyBytes;
    private final String backupPublicKeySpkiBase64;
    private final PrivateKey backupPrivateKey;

    public CryptoService(CryptoProperties properties) {
        if (properties == null) {
            throw new IllegalStateException("CryptoProperties is null");
        }

        this.serverAesKeyBytes = decodeAes256Key(properties.getServerAesKeyBase64());
        this.backupPublicKeySpkiBase64 = requireNonBlank(properties.getBackupRsaPublicKeySpkiBase64(),
                "yusi.security.crypto.backup-rsa-public-key-spki-base64");
        this.backupPrivateKey = loadPrivateKey(properties.getBackupRsaPrivateKeyPkcs8Base64());
    }

    public byte[] serverAesKeyBytes() {
        return serverAesKeyBytes;
    }

    public String backupPublicKeySpkiBase64() {
        return backupPublicKeySpkiBase64;
    }

    public byte[] decryptBackupKeyBase64(String encryptedBackupKeyBase64) {
        if (StrUtil.isBlank(encryptedBackupKeyBase64)) {
            throw new IllegalStateException("encryptedBackupKey is blank");
        }
        byte[] encrypted;
        try {
            encrypted = Base64.getDecoder().decode(encryptedBackupKeyBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("encryptedBackupKey is not valid Base64", e);
        }

        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            cipher.init(Cipher.DECRYPT_MODE, backupPrivateKey, oaepSha256());
            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("RSA-OAEP decryption failed", e);
        }
    }

    /**
     * Encrypts a short-lived recovery payload for the browser-provided public key.
     * The corresponding private key never leaves the browser.
     */
    public String encryptForRecovery(byte[] plaintext, String recoveryPublicKeySpkiBase64) {
        if (plaintext == null || plaintext.length == 0) {
            throw new IllegalArgumentException("Recovery payload must not be empty");
        }
        PublicKey recoveryPublicKey = loadPublicKey(recoveryPublicKeySpkiBase64);
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            cipher.init(Cipher.ENCRYPT_MODE, recoveryPublicKey, oaepSha256());
            return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext));
        } catch (Exception e) {
            throw new IllegalStateException("RSA-OAEP recovery encryption failed", e);
        }
    }

    private static byte[] decodeAes256Key(String base64) {
        String v = requireNonBlank(base64, "yusi.security.crypto.server-aes-key-base64");
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Server AES key is not valid Base64", e);
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException("Server AES key must be 32 bytes after Base64 decode.");
        }
        return keyBytes;
    }

    private static PrivateKey loadPrivateKey(String pkcs8Base64) {
        String v = requireNonBlank(pkcs8Base64, "yusi.security.crypto.backup-rsa-private-key-pkcs8-base64");
        byte[] pkcs8;
        try {
            pkcs8 = Base64.getDecoder().decode(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Backup RSA private key is not valid Base64", e);
        }

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA private key", e);
        }
    }

    private static PublicKey loadPublicKey(String spkiBase64) {
        String v = requireNonBlank(spkiBase64, "recovery public key");
        byte[] spki;
        try {
            spki = Base64.getDecoder().decode(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Recovery public key is not valid Base64", e);
        }

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(new X509EncodedKeySpec(spki));
        } catch (Exception e) {
            throw new IllegalArgumentException("Recovery public key is invalid", e);
        }
    }

    private static OAEPParameterSpec oaepSha256() {
        return new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT
        );
    }

    private static String requireNonBlank(String value, String name) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }
}
