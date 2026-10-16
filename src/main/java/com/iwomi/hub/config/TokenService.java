package com.iwomi.hub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/** Issues and verifies signed login tokens: base64url(userId:expiry).base64url(HMAC-SHA256). */
@Service
public class TokenService {

    private static final long TTL_SECONDS = 12L * 3600;
    private final byte[] secret;

    public TokenService(@Value("${hub.security.token-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(Long userId) {
        long exp = Instant.now().getEpochSecond() + TTL_SECONDS;
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((userId + ":" + exp).getBytes(StandardCharsets.UTF_8));
        return payload + "." + sign(payload);
    }

    /** Returns the user id when the token is authentic and not expired. */
    public Optional<Long> verify(String token) {
        try {
            int dot = token.indexOf('.');
            if (dot < 1) return Optional.empty();
            String payload = token.substring(0, dot);
            String signature = token.substring(dot + 1);
            if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }
            String decoded = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            if (Long.parseLong(parts[1]) < Instant.now().getEpochSecond()) return Optional.empty();
            return Optional.of(Long.parseLong(parts[0]));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign token", e);
        }
    }
}
