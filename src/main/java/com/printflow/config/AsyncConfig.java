package com.printflow.config;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${app.notification.email.async:true}")
    private boolean asyncEmailEnabled;

    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        if (!asyncEmailEnabled) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("email-");
        executor.initialize();
        return executor;
    }
}
