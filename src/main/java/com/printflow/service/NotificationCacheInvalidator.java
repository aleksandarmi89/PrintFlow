package com.printflow.service;

import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationCacheInvalidator {

    private final CacheManager cacheManager;

    public NotificationCacheInvalidator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @EventListener
    public void onNotificationsCreated(NotificationBatchCreatedEvent event) {
        if (event == null || event.getUserIds() == null || event.getUserIds().isEmpty()) {
            return;
        }
        var cache = cacheManager.getCache("notificationCounts");
        var recentCache = cacheManager.getCache("recentNotifications");
        if (cache == null && recentCache == null) {
            return;
        }
        for (Long userId : event.getUserIds()) {
            // User.id is globally unique, so userId alone is sufficient for cache keys.
            if (cache != null) {
                cache.evict("u:" + userId);
            }
            if (recentCache != null) {
                recentCache.evict("u:" + userId);
            }
        }
    }
}
