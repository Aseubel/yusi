package com.aseubel.yusi.common.utils;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesGcmCryptoUtilsTest {

    private static final byte[] KEY_256 = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void encryptsAndDecryptsUtf8Text() {
        String plaintext = "今天的日记：稳定、清晰、可恢复。";

        String encrypted = AesGcmCryptoUtils.encryptText(plaintext, KEY_256);

        assertNotEquals(plaintext, encrypted);
        assertEquals(plaintext, AesGcmCryptoUtils.decryptText(encrypted, KEY_256));
    }

    @Test
    void encryptingSameTextTwiceUsesDifferentIv() {
        String plaintext = "same input";

        String first = AesGcmCryptoUtils.encryptText(plaintext, KEY_256);
        String second = AesGcmCryptoUtils.encryptText(plaintext, KEY_256);

        assertNotEquals(first, second);
        assertEquals(plaintext, AesGcmCryptoUtils.decryptText(first, KEY_256));
        assertEquals(plaintext, AesGcmCryptoUtils.decryptText(second, KEY_256));
    }

    @Test
    void base64KeyOverloadRoundTripsAndAllowsNullPlaintext() {
        String keyBase64 = Base64.getEncoder().encodeToString(KEY_256);

        String encrypted = AesGcmCryptoUtils.encryptText("hello", keyBase64);

        assertEquals("hello", AesGcmCryptoUtils.decryptText(encrypted, keyBase64));
        assertNull(AesGcmCryptoUtils.encryptText(null, keyBase64));
        assertNull(AesGcmCryptoUtils.decryptText(null, keyBase64));
    }

    @Test
    void deriveKeyFromPasswordIsDeterministicForSameSalt() {
        String saltBase64 = Base64.getEncoder().encodeToString("stable-salt".getBytes(StandardCharsets.UTF_8));

        byte[] first = AesGcmCryptoUtils.deriveKeyFromPassword("password", saltBase64);
        byte[] second = AesGcmCryptoUtils.deriveKeyFromPassword("password", saltBase64);

        assertArrayEquals(first, second);
        assertEquals(32, first.length);
    }

    @Test
    void rejectsInvalidInputsWithUsefulFailures() {
        assertThrows(IllegalStateException.class, () -> AesGcmCryptoUtils.encryptText("x", new byte[7]));
        assertThrows(IllegalStateException.class, () -> AesGcmCryptoUtils.decryptText("not-base64", KEY_256));
        assertThrows(IllegalStateException.class, () -> AesGcmCryptoUtils.decryptText(Base64.getEncoder().encodeToString(new byte[12]), KEY_256));
        assertThrows(IllegalStateException.class, () -> AesGcmCryptoUtils.deriveKeyFromPassword("password", "not-base64"));
    }
}
