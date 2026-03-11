package com.printflow.service;

import com.printflow.repository.BannedIpRepository;
import com.printflow.repository.WhitelistedIpRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitServiceTest {

    @Test
    void deniedCounterIncrementsOnLimitExceeded() {
        BannedIpRepository bannedIpRepository = mock(BannedIpRepository.class);
        WhitelistedIpRepository whitelistedIpRepository = mock(WhitelistedIpRepository.class);
        when(bannedIpRepository.findByActiveTrueOrderByCreatedAtDesc()).thenReturn(java.util.List.of());
        when(whitelistedIpRepository.findByActiveTrueOrderByCreatedAtDesc()).thenReturn(java.util.List.of());

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RateLimitService service = new RateLimitService(
            false, "", false, "",
            false, 10, 300, 3600, 3600,
            bannedIpRepository, whitelistedIpRepository,
            Optional.of(registry)
        );

        assertTrue(service.allow("public:127.0.0.1", 1, 60_000));
        assertFalse(service.allow("public:127.0.0.1", 1, 60_000));

        assertEquals(1.0d, registry.get("printflow_rate_limit_denied_total").counter().count());
        assertEquals(0.0d, registry.get("printflow_rate_limit_auto_ban_total").counter().count());
    }

    @Test
    void autoBanCounterIncrementsWhenThresholdReached() {
        BannedIpRepository bannedIpRepository = mock(BannedIpRepository.class);
        WhitelistedIpRepository whitelistedIpRepository = mock(WhitelistedIpRepository.class);
        when(bannedIpRepository.findByActiveTrueOrderByCreatedAtDesc()).thenReturn(java.util.List.of());
        when(whitelistedIpRepository.findByActiveTrueOrderByCreatedAtDesc()).thenReturn(java.util.List.of());
        when(bannedIpRepository.findByIp("203.0.113.9")).thenReturn(Optional.empty());
        when(bannedIpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RateLimitService service = new RateLimitService(
            false, "", false, "",
            true, 1, 300, 3600, 3600,
            bannedIpRepository, whitelistedIpRepository,
            Optional.of(registry)
        );

        assertTrue(service.allow("public-global:203.0.113.9", 1, 60_000));
        assertFalse(service.allow("public-global:203.0.113.9", 1, 60_000));

        assertEquals(1.0d, registry.get("printflow_rate_limit_denied_total").counter().count());
        assertEquals(1.0d, registry.get("printflow_rate_limit_auto_ban_total").counter().count());
        verify(bannedIpRepository).save(any());
    }
}

