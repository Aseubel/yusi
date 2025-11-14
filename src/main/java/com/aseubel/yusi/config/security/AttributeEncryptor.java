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
        if (k == null || k.length() < 16) return null;
        byte[] bytes = k.substring(0, 16).getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(bytes, ALG);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        try {
            SecretKey key = key();
            if (key == null || attribute == null) return attribute;
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
        } catch (Exception e) {
            return attribute;
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        try {
            SecretKey key = key();
            if (key == null || dbData == null) return dbData;
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
        } catch (Exception e) {
            return dbData;
        }
    }
}