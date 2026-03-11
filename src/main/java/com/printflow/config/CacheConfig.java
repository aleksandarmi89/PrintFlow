package com.printflow.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.cache")
    public CacheTtlProperties cacheTtlProperties() {
        return new CacheTtlProperties();
    }

    @Bean
    public CacheManager cacheManager(CacheTtlProperties props) {
        CaffeineCache notificationCounts = new CaffeineCache(
            "notificationCounts",
            Caffeine.newBuilder()
                .recordStats()
                .expireAfterWrite(Duration.ofSeconds(props.getNotificationCountTtlSeconds()))
                .maximumSize(props.getNotificationCountMaxSize())
                .build()
        );
        CaffeineCache recentNotifications = new CaffeineCache(
            "recentNotifications",
            Caffeine.newBuilder()
                .recordStats()
                .expireAfterWrite(Duration.ofSeconds(props.getRecentNotificationsTtlSeconds()))
                .maximumSize(props.getRecentNotificationsMaxSize())
                .build()
        );
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(notificationCounts, recentNotifications));
        return manager;
    }

    public static class CacheTtlProperties {
        private long notificationCountTtlSeconds = 20;
        private long notificationCountMaxSize = 10000;
        private long recentNotificationsTtlSeconds = 8;
        private long recentNotificationsMaxSize = 10000;

        public long getNotificationCountTtlSeconds() {
            return notificationCountTtlSeconds;
        }

        public void setNotificationCountTtlSeconds(long notificationCountTtlSeconds) {
            this.notificationCountTtlSeconds = notificationCountTtlSeconds;
        }

        public long getNotificationCountMaxSize() {
            return notificationCountMaxSize;
        }

        public void setNotificationCountMaxSize(long notificationCountMaxSize) {
            this.notificationCountMaxSize = notificationCountMaxSize;
        }

        public long getRecentNotificationsTtlSeconds() {
            return recentNotificationsTtlSeconds;
        }

        public void setRecentNotificationsTtlSeconds(long recentNotificationsTtlSeconds) {
            this.recentNotificationsTtlSeconds = recentNotificationsTtlSeconds;
        }

        public long getRecentNotificationsMaxSize() {
            return recentNotificationsMaxSize;
        }

        public void setRecentNotificationsMaxSize(long recentNotificationsMaxSize) {
            this.recentNotificationsMaxSize = recentNotificationsMaxSize;
        }
    }
}
