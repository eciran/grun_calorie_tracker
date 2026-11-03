package com.grun.calorietracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class BrandedDuplicateBatchConfig {
    @Bean(name = "brandedDuplicateBatchExecutor")
    public Executor brandedDuplicateBatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("branded-duplicate-batch-");
        executor.initialize();
        return executor;
    }
}
