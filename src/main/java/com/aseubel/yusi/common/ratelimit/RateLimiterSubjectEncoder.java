package com.aseubel.yusi.common.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Encodes rate-limit subjects without retaining user or network identifiers. */
@Component
public final class RateLimiterSubjectEncoder {

    static final String SECRET_ENV = "YUSI_RATE_LIMIT_HMAC_SECRET";
    private static final String ALGORITHM = "HmacSHA256";
    private static final int SECRET_BYTES = 32;

    private final byte[] secret;
    private final boolean configured;

    @org.springframework.beans.factory.annotation.Autowired
    public RateLimiterSubjectEncoder(
            @Value("${yusi.rate-limit.hmac-secret:}") String configuredSecret) {
        this(configuredSecret, new SecureRandom());
    }

    private RateLimiterSubjectEncoder(String configuredSecret, SecureRandom random) {
        this.configured = configuredSecret != null && !configuredSecret.isBlank();
        if (configured) {
            this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        } else {
            // Isolated callers still get a non-plaintext key; the aspect refuses
            // subject-scoped traffic until the deployment secret is configured.
            this.secret = new byte[SECRET_BYTES];
            random.nextBytes(this.secret);
        }
    }

    static RateLimiterSubjectEncoder fromEnvironment() {
        return new RateLimiterSubjectEncoder(System.getenv(SECRET_ENV), new SecureRandom());
    }

    public boolean isConfigured() {
        return configured;
    }

    public String encode(String subject) {
        String safeSubject = subject == null || subject.isBlank() ? "unknown" : subject;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(safeSubject.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("rate_limit_subject_encoding_failed");
        }
    }
}
