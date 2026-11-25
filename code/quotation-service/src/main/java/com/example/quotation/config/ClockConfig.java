package com.example.quotation.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    // Toute date « d'aujourd'hui » passe par cette horloge : on peut la remplacer dans un test
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
