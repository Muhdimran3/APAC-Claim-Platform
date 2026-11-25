package com.example.claims.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class AppConfig {

    /** Injected wherever "now" is needed so tests can control time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
