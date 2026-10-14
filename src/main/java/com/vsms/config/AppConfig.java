package com.vsms.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** One shared clock so "today" and "now" can be replaced in tests. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
