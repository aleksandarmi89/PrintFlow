package com.printflow.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class PublicTokenService {

    public record TokenInfo(String token, LocalDateTime createdAt, LocalDateTime expiresAt) {}

    private final SecureRandom secureRandom = new SecureRandom();
    private final int tokenBytes;
    private final int ttlDays;
    private final Clock clock;

    public PublicTokenService(@Value("${app.public-order.token.bytes:32}") int tokenBytes,
                              @Value("${app.public-order.token.ttl-days:30}") int ttlDays,
                              Clock clock) {
        this.tokenBytes = Math.max(16, tokenBytes);
        this.ttlDays = Math.max(1, ttlDays);
        this.clock = clock;
    }

    public TokenInfo newToken() {
        LocalDateTime now = LocalDateTime.now(clock);
        return new TokenInfo(generateToken(), now, now.plusDays(ttlDays));
    }

    public boolean isExpired(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            return true;
        }
        return !expiresAt.isAfter(LocalDateTime.now(clock));
    }

    public LocalDateTime expiresAtFromNow() {
        return LocalDateTime.now(clock).plusDays(ttlDays);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String generateToken() {
        byte[] bytes = new byte[tokenBytes];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
