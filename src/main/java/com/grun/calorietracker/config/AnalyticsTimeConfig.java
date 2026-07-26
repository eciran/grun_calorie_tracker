package com.grun.calorietracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AnalyticsTimeConfig {

    @Bean
    public Clock analyticsClock() {
        return Clock.systemUTC();
    }
}
