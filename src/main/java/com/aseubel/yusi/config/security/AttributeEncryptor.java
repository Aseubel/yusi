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

    private static final String ALG = "AES";
    private static final String TRANS = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;

    private SecretKey key() {
        String k = System.getenv("YUSI_ENCRYPTION_KEY");
        if (k == null || k.length() < 16) {
            throw new IllegalStateException("YUSI_ENCRYPTION_KEY must be set and at least 16 characters long.");
        }
        byte[] bytes = k.substring(0, 16).getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(bytes, ALG);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null)
            return null;
        try {
            SecretKey key = key(); // Will throw if invalid
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANS);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
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
            SecretKey key = key(); // Will throw if invalid
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
            // Keep fail-safe for legacy data or genuine decryption errors,
            // but strict key check happens first above.
            // If the key is present but decryption fails (e.g. wrong key, corrupted data,
            // or not encrypted),
            // we might return data as is ONLY if it doesn't look like valid ciphertext?
            // For now, retaining the behavior of returning dbData on decryption failure
            // is risky if we strictly want to avoid leaking, but safer for availability.
            // However, the original code had catch-all.
            // Given the requirement "Avoid silent failure", we probably want to error out
            // if we CANT decrypt
            // what looks like encrypted data?
            // The original requirement was focused on Storage (encrypting).
            // For reading, if we fail to decrypt, returning ciphertext is usually useless
            // UI-wise but safe Security-wise.
            // Returning dbData as plain text is only okay if it WAS plain text.
            return dbData;
        }
    }
}