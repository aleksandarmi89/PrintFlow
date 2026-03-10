package com.printflow.service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TenantEmailRateLimiter {

    private final int maxPerMinute;
    private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();

    public TenantEmailRateLimiter(@Value("${app.notification.email.rate-limit-per-minute:60}") int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }

    public boolean tryAcquire(Long companyId) {
        if (companyId == null) {
            return true;
        }
        long now = Instant.now().getEpochSecond();
        Window window = windows.compute(companyId, (id, existing) -> {
            if (existing == null || now >= existing.windowStart + 60) {
                return new Window(now);
            }
            return existing;
        });
        return window.count.incrementAndGet() <= maxPerMinute;
    }

    private static class Window {
        final long windowStart;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
