package com.printflow.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicTokenServiceTest {

    @Test
    void isExpired_isTrueForNullAndPastAndNow() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-12T12:00:00Z"), ZoneOffset.UTC);
        PublicTokenService service = new PublicTokenService(32, 30, fixedClock);
        LocalDateTime now = LocalDateTime.now(fixedClock);

        assertTrue(service.isExpired(null));
        assertTrue(service.isExpired(now.minusSeconds(1)));
        assertTrue(service.isExpired(now));
    }

    @Test
    void isExpired_isFalseForFutureTimestamp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-12T12:00:00Z"), ZoneOffset.UTC);
        PublicTokenService service = new PublicTokenService(32, 30, fixedClock);
        LocalDateTime now = LocalDateTime.now(fixedClock);

        assertFalse(service.isExpired(now.plusSeconds(1)));
    }
}
