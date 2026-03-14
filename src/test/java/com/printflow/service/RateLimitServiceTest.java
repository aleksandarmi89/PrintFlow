package com.printflow.service;

import com.printflow.repository.BannedIpRepository;
import com.printflow.repository.WhitelistedIpRepository;
import com.printflow.entity.BannedIp;
import com.printflow.entity.WhitelistedIp;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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

    @Test
    void autoBanUsesFullIpv6FromRateLimitKey() {
        BannedIpRepository bannedIpRepository = mock(BannedIpRepository.class);
        WhitelistedIpRepository whitelistedIpRepository = mock(WhitelistedIpRepository.class);
        when(bannedIpRepository.findByActiveTrueOrderByCreatedAtDesc()).thenReturn(java.util.List.of());
        when(whitelistedIpRepository.findByActiveTrueOrderByCreatedAtDesc()).thenReturn(java.util.List.of());
        when(bannedIpRepository.findByIp("2001:db8::9")).thenReturn(Optional.empty());
        when(bannedIpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RateLimitService service = new RateLimitService(
            false, "", false, "",
            true, 1, 300, 3600, 3600,
            bannedIpRepository, whitelistedIpRepository,
            Optional.of(registry)
        );

        assertTrue(service.allow("public-global:2001:db8::9", 1, 60_000));
        assertFalse(service.allow("public-global:2001:db8::9", 1, 60_000));

        verify(bannedIpRepository).findByIp("2001:db8::9");
        verify(bannedIpRepository).save(argThat(entity ->
            "2001:db8::9".equals(entity.getIp())
        ));
        assertEquals(1.0d, registry.get("printflow_rate_limit_auto_ban_total").counter().count());
    }

    @Test
    void banNormalizesIpAndReasonFallbackToManual() {
        BannedIpRepository bannedIpRepository = mock(BannedIpRepository.class);
        WhitelistedIpRepository whitelistedIpRepository = mock(WhitelistedIpRepository.class);
        when(bannedIpRepository.findByActiveTrueOrderByCreatedAtDesc()).thenReturn(java.util.List.of());
        when(whitelistedIpRepository.findByActiveTrueOrderByCreatedAtDesc()).thenReturn(java.util.List.of());
        when(bannedIpRepository.findByIp("198.51.100.7")).thenReturn(Optional.empty());
        when(bannedIpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RateLimitService service = new RateLimitService(
            false, "", false, "",
            false, 10, 300, 3600, 3600,
            bannedIpRepository, whitelistedIpRepository,
            Optional.empty()
        );

        service.ban(" 198.51.100.7 ", "   ", null);

        verify(bannedIpRepository).findByIp("198.51.100.7");
        verify(bannedIpRepository).save(argThat(entity ->
            "198.51.100.7".equals(entity.getIp()) && "manual".equals(entity.getReason())
        ));
        assertTrue(service.isBanned("198.51.100.7"));
        assertTrue(service.isBanned(" 198.51.100.7 "));
    }

    @Test
    void whitelistAndUnbanNormalizeIp() {
        BannedIpRepository bannedIpRepository = mock(BannedIpRepository.class);
        WhitelistedIpRepository whitelistedIpRepository = mock(WhitelistedIpRepository.class);
        when(bannedIpRepository.findByActiveTrueOrderByCreatedAtDesc()).thenReturn(java.util.List.of());
        when(whitelistedIpRepository.findByActiveTrueOrderByCreatedAtDesc()).thenReturn(java.util.List.of());
        when(whitelistedIpRepository.findByIp("203.0.113.77")).thenReturn(Optional.empty());
        when(bannedIpRepository.findByIp("203.0.113.77")).thenReturn(Optional.empty());

        RateLimitService service = new RateLimitService(
            false, "", false, "",
            false, 10, 300, 3600, 3600,
            bannedIpRepository, whitelistedIpRepository,
            Optional.empty()
        );

        service.whitelist(" 203.0.113.77 ");
        assertTrue(service.isWhitelisted("203.0.113.77"));
        assertTrue(service.isWhitelisted(" 203.0.113.77 "));

        service.ban("203.0.113.77", "x", null);
        assertFalse(service.isBanned("203.0.113.77"));

        service.unban(" 203.0.113.77 ");
        service.unwhitelist(" 203.0.113.77 ");
        verify(whitelistedIpRepository, times(2)).findByIp("203.0.113.77");
        verify(bannedIpRepository, times(2)).findByIp("203.0.113.77");
    }

    @Test
    void initBanListNormalizesConfiguredAndPersistedIps() {
        BannedIpRepository bannedIpRepository = mock(BannedIpRepository.class);
        WhitelistedIpRepository whitelistedIpRepository = mock(WhitelistedIpRepository.class);

        BannedIp persistedBan = new BannedIp();
        persistedBan.setIp(" 198.51.100.2 ");
        WhitelistedIp persistedWhitelist = new WhitelistedIp();
        persistedWhitelist.setIp(" 203.0.113.2 ");

        when(bannedIpRepository.findByActiveTrueOrderByCreatedAtDesc()).thenReturn(List.of(persistedBan));
        when(whitelistedIpRepository.findByActiveTrueOrderByCreatedAtDesc()).thenReturn(List.of(persistedWhitelist));

        RateLimitService service = new RateLimitService(
            true, " 198.51.100.1 , 198.51.100.2 ",
            true, " 203.0.113.1 , 203.0.113.2 ",
            false, 10, 300, 3600, 3600,
            bannedIpRepository, whitelistedIpRepository,
            Optional.empty()
        );
        service.initBanList();

        assertTrue(service.isBanned("198.51.100.1"));
        assertTrue(service.isBanned(" 198.51.100.2 "));
        assertTrue(service.isWhitelisted("203.0.113.1"));
        assertTrue(service.isWhitelisted(" 203.0.113.2 "));
    }

    @Test
    void initBanListClearsStaleInMemoryEntriesOnReload() {
        BannedIpRepository bannedIpRepository = mock(BannedIpRepository.class);
        WhitelistedIpRepository whitelistedIpRepository = mock(WhitelistedIpRepository.class);

        BannedIp persistedBan = new BannedIp();
        persistedBan.setIp("198.51.100.20");
        WhitelistedIp persistedWhitelist = new WhitelistedIp();
        persistedWhitelist.setIp("203.0.113.20");

        when(bannedIpRepository.findByActiveTrueOrderByCreatedAtDesc())
            .thenReturn(List.of(persistedBan))
            .thenReturn(List.of());
        when(whitelistedIpRepository.findByActiveTrueOrderByCreatedAtDesc())
            .thenReturn(List.of(persistedWhitelist))
            .thenReturn(List.of());

        RateLimitService service = new RateLimitService(
            false, "",
            false, "",
            false, 10, 300, 3600, 3600,
            bannedIpRepository, whitelistedIpRepository,
            Optional.empty()
        );

        service.initBanList();
        assertTrue(service.isBanned("198.51.100.20"));
        assertTrue(service.isWhitelisted("203.0.113.20"));

        service.initBanList();
        assertFalse(service.isBanned("198.51.100.20"));
        assertFalse(service.isWhitelisted("203.0.113.20"));
    }
}
