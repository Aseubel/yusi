package com.aseubel.yusi.config.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoServiceRecoveryTest {

    @Test
    void encryptsTheBackupKeyForTheBrowserRecoveryPublicKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair serverKeyPair = keyPairGenerator.generateKeyPair();
        KeyPair browserKeyPair = keyPairGenerator.generateKeyPair();

        CryptoProperties properties = new CryptoProperties();
        properties.setServerAesKeyBase64(Base64.getEncoder().encodeToString(new byte[32]));
        properties.setBackupRsaPublicKeySpkiBase64(
                Base64.getEncoder().encodeToString(serverKeyPair.getPublic().getEncoded()));
        properties.setBackupRsaPrivateKeyPkcs8Base64(
                Base64.getEncoder().encodeToString(serverKeyPair.getPrivate().getEncoded()));

        CryptoService cryptoService = new CryptoService(properties);
        Method method = CryptoService.class.getMethod("encryptForRecovery", byte[].class, String.class);
        byte[] secret = "recovery-secret".getBytes(StandardCharsets.UTF_8);

        String encrypted = (String) method.invoke(cryptoService, secret,
                Base64.getEncoder().encodeToString(browserKeyPair.getPublic().getEncoded()));

        assertThat(decrypt(encrypted, browserKeyPair.getPrivate())).isEqualTo(secret);
    }

    private byte[] decrypt(String encryptedBase64, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey, new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        return cipher.doFinal(Base64.getDecoder().decode(encryptedBase64));
    }
}
