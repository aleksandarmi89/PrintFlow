package com.printflow.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import jakarta.annotation.PostConstruct;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {
    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final ConcurrentHashMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();
    private final Set<String> bannedIps = ConcurrentHashMap.newKeySet();
    private final Set<String> whitelistedIps = ConcurrentHashMap.newKeySet();
    private final boolean banListEnabled;
    private final String banListIps;
    private final boolean whitelistEnabled;
    private final String whitelistIps;
    private final boolean autoBanEnabled;
    private final int autoBanThreshold;
    private final long autoBanWindowMillis;
    private final long autoBanBanMillis;
    private final ConcurrentHashMap<String, Deque<Long>> violations = new ConcurrentHashMap<>();
    private final long dashboardWindowMillis;
    private final com.printflow.repository.BannedIpRepository bannedIpRepository;
    private final com.printflow.repository.WhitelistedIpRepository whitelistedIpRepository;
    private final Counter deniedCounter;
    private final Counter autoBanCounter;

    public RateLimitService(@Value("${app.rate-limit.ban-list.enabled:false}") boolean banListEnabled,
                            @Value("${app.rate-limit.ban-list.ips:}") String banListIps,
                            @Value("${app.rate-limit.white-list.enabled:false}") boolean whitelistEnabled,
                            @Value("${app.rate-limit.white-list.ips:}") String whitelistIps,
                            @Value("${app.rate-limit.auto-ban.enabled:false}") boolean autoBanEnabled,
                            @Value("${app.rate-limit.auto-ban.threshold:10}") int autoBanThreshold,
                            @Value("${app.rate-limit.auto-ban.window-seconds:300}") int autoBanWindowSeconds,
                            @Value("${app.rate-limit.auto-ban.ban-seconds:3600}") int autoBanBanSeconds,
                            @Value("${app.rate-limit.dashboard.window-seconds:3600}") int dashboardWindowSeconds,
                            com.printflow.repository.BannedIpRepository bannedIpRepository,
                            com.printflow.repository.WhitelistedIpRepository whitelistedIpRepository,
                            Optional<MeterRegistry> meterRegistry) {
        this.banListEnabled = banListEnabled;
        this.banListIps = banListIps;
        this.whitelistEnabled = whitelistEnabled;
        this.whitelistIps = whitelistIps;
        this.autoBanEnabled = autoBanEnabled;
        this.autoBanThreshold = autoBanThreshold;
        this.autoBanWindowMillis = autoBanWindowSeconds * 1000L;
        this.autoBanBanMillis = autoBanBanSeconds * 1000L;
        this.dashboardWindowMillis = dashboardWindowSeconds * 1000L;
        this.bannedIpRepository = bannedIpRepository;
        this.whitelistedIpRepository = whitelistedIpRepository;
        this.deniedCounter = meterRegistry.map(registry ->
            Counter.builder("printflow_rate_limit_denied_total")
                .description("Total number of denied requests by rate limiter")
                .register(registry))
            .orElse(null);
        this.autoBanCounter = meterRegistry.map(registry ->
            Counter.builder("printflow_rate_limit_auto_ban_total")
                .description("Total number of auto-banned IPs")
                .register(registry))
            .orElse(null);
    }

    @PostConstruct
    public void initBanList() {
        if (!banListEnabled || banListIps == null || banListIps.isBlank()) {
            // still load from DB
        } else {
            String[] items = banListIps.split(",");
            for (String item : items) {
                String ip = item.trim();
                if (!ip.isEmpty()) {
                    bannedIps.add(ip);
                }
            }
        }
        bannedIpRepository.findByActiveTrueOrderByCreatedAtDesc()
            .forEach(b -> bannedIps.add(b.getIp()));

        if (whitelistEnabled && whitelistIps != null && !whitelistIps.isBlank()) {
            String[] items = whitelistIps.split(",");
            for (String item : items) {
                String ip = item.trim();
                if (!ip.isEmpty()) {
                    whitelistedIps.add(ip);
                }
            }
        }
        whitelistedIpRepository.findByActiveTrueOrderByCreatedAtDesc()
            .forEach(w -> whitelistedIps.add(w.getIp()));
    }

    public boolean isBanned(String ip) {
        if (ip == null) {
            return false;
        }
        if (isWhitelisted(ip)) {
            return false;
        }
        cleanupExpiredBans();
        if (bannedIps.contains(ip)) {
            return true;
        }
        // CIDR support
        for (String pattern : bannedIps) {
            if (pattern != null && pattern.contains("/") && isIpInCidr(ip, pattern)) {
                return true;
            }
        }
        return false;
    }

    public void ban(String ip) {
        ban(ip, "manual", null);
    }

    public void ban(String ip, String reason, java.time.LocalDateTime expiresAt) {
        if (ip != null && !ip.isBlank()) {
            bannedIps.add(ip);
            bannedIpRepository.findByIp(ip).ifPresentOrElse(
                existing -> {
                    existing.setActive(true);
                    existing.setReason(reason);
                    existing.setExpiresAt(expiresAt);
                    bannedIpRepository.save(existing);
                },
                () -> {
                    com.printflow.entity.BannedIp entity = new com.printflow.entity.BannedIp(ip);
                    entity.setReason(reason);
                    entity.setExpiresAt(expiresAt);
                    bannedIpRepository.save(entity);
                }
            );
        }
    }

    public void unban(String ip) {
        if (ip != null && !ip.isBlank()) {
            bannedIps.remove(ip);
            bannedIpRepository.findByIp(ip).ifPresent(existing -> {
                existing.setActive(false);
                bannedIpRepository.save(existing);
            });
        }
    }

    public Set<String> getBannedIps() {
        return bannedIps;
    }

    public java.util.List<com.printflow.entity.BannedIp> getActiveBannedIps() {
        cleanupExpiredBans();
        return bannedIpRepository.findByActiveTrueOrderByCreatedAtDesc();
    }

    public boolean isWhitelisted(String ip) {
        if (ip == null) {
            return false;
        }
        if (whitelistedIps.contains(ip)) {
            return true;
        }
        for (String pattern : whitelistedIps) {
            if (pattern != null && pattern.contains("/") && isIpInCidr(ip, pattern)) {
                return true;
            }
        }
        return false;
    }

    public void whitelist(String ip) {
        if (ip != null && !ip.isBlank()) {
            whitelistedIps.add(ip);
            whitelistedIpRepository.findByIp(ip).ifPresentOrElse(
                existing -> {
                    existing.setActive(true);
                    whitelistedIpRepository.save(existing);
                },
                () -> whitelistedIpRepository.save(new com.printflow.entity.WhitelistedIp(ip))
            );
        }
    }

    public void unwhitelist(String ip) {
        if (ip != null && !ip.isBlank()) {
            whitelistedIps.remove(ip);
            whitelistedIpRepository.findByIp(ip).ifPresent(existing -> {
                existing.setActive(false);
                whitelistedIpRepository.save(existing);
            });
        }
    }

    public Set<String> getWhitelistedIps() {
        return whitelistedIps;
    }

    public java.util.List<com.printflow.entity.WhitelistedIp> getActiveWhitelistedIps() {
        return whitelistedIpRepository.findByActiveTrueOrderByCreatedAtDesc();
    }

    public boolean allow(String key, int maxRequests, long windowMillis) {
        if (key == null || key.isBlank()) {
            return true;
        }
        long now = System.currentTimeMillis();
        Deque<Long> queue = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() > windowMillis) {
                queue.pollFirst();
            }
            if (queue.size() >= maxRequests) {
                if (deniedCounter != null) {
                    deniedCounter.increment();
                }
                log.warn("rate_limit_denied key={} maxRequests={} windowMs={} currentSize={}",
                    key, maxRequests, windowMillis, queue.size());
                recordViolation(key);
                return false;
            }
            queue.addLast(now);
            return true;
        }
    }

    private void recordViolation(String key) {
        if (!autoBanEnabled || key == null) {
            return;
        }
        String ip = extractIpFromKey(key);
        if (ip == null || ip.isBlank() || isWhitelisted(ip)) {
            return;
        }
        long now = System.currentTimeMillis();
        Deque<Long> queue = violations.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() > autoBanWindowMillis) {
                queue.pollFirst();
            }
            queue.addLast(now);
            if (queue.size() >= autoBanThreshold) {
                java.time.LocalDateTime expiresAt = java.time.LocalDateTime.now()
                    .plusSeconds(autoBanBanMillis / 1000L);
                ban(ip, "auto-ban: rate limit exceeded", expiresAt);
                if (autoBanCounter != null) {
                    autoBanCounter.increment();
                }
                log.warn("rate_limit_auto_ban ip={} threshold={} windowMs={} banMs={} expiresAt={}",
                    ip, autoBanThreshold, autoBanWindowMillis, autoBanBanMillis, expiresAt);
            }
        }
    }

    private String extractIpFromKey(String key) {
        int idx = key.lastIndexOf(':');
        if (idx == -1 || idx == key.length() - 1) {
            return null;
        }
        return key.substring(idx + 1);
    }

    private void cleanupExpiredBans() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (com.printflow.entity.BannedIp ban : bannedIpRepository.findByActiveTrueOrderByCreatedAtDesc()) {
            if (ban.getExpiresAt() != null && ban.getExpiresAt().isBefore(now)) {
                ban.setActive(false);
                bannedIpRepository.save(ban);
                bannedIps.remove(ban.getIp());
            }
        }
    }

    private boolean isIpInCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) return false;
            java.net.InetAddress addr = java.net.InetAddress.getByName(parts[0]);
            java.net.InetAddress target = java.net.InetAddress.getByName(ip);
            int prefix = Integer.parseInt(parts[1]);
            byte[] addrBytes = addr.getAddress();
            byte[] targetBytes = target.getAddress();
            if (addrBytes.length != targetBytes.length) {
                return false;
            }
            int fullBytes = prefix / 8;
            int remainder = prefix % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (addrBytes[i] != targetBytes[i]) return false;
            }
            if (remainder == 0) return true;
            int mask = ~((1 << (8 - remainder)) - 1);
            return (addrBytes[fullBytes] & mask) == (targetBytes[fullBytes] & mask);
        } catch (Exception ex) {
            return false;
        }
    }

    public List<ViolationStat> getTopViolations(int limit) {
        return getTopViolations(limit, dashboardWindowMillis);
    }

    public List<ViolationStat> getTopViolations(int limit, long windowMillis) {
        long now = System.currentTimeMillis();
        List<ViolationStat> stats = new java.util.ArrayList<>();
        violations.forEach((ip, queue) -> {
            int count;
            synchronized (queue) {
                while (!queue.isEmpty() && now - queue.peekFirst() > windowMillis) {
                    queue.pollFirst();
                }
                count = queue.size();
            }
            if (count > 0) {
                stats.add(new ViolationStat(ip, count));
            }
        });
        stats.sort((a, b) -> Integer.compare(b.count, a.count));
        if (stats.size() > limit) {
            return stats.subList(0, limit);
        }
        return stats;
    }

    public int getTotalViolations() {
        return getTotalViolations(dashboardWindowMillis);
    }

    public int getTotalViolations(long windowMillis) {
        long now = System.currentTimeMillis();
        int total = 0;
        for (Deque<Long> queue : violations.values()) {
            synchronized (queue) {
                while (!queue.isEmpty() && now - queue.peekFirst() > windowMillis) {
                    queue.pollFirst();
                }
                total += queue.size();
            }
        }
        return total;
    }

    public void clearInMemoryState() {
        buckets.clear();
        violations.clear();
    }

    public static class ViolationStat {
        private final String ip;
        private final int count;

        public ViolationStat(String ip, int count) {
            this.ip = ip;
            this.count = count;
        }

        public String getIp() { return ip; }
        public int getCount() { return count; }
    }
}
