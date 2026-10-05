package com.library.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BorrowingConfiguration {

    @Bean
    Clock applicationClock() {
        return Clock.systemDefaultZone();
    }
}
