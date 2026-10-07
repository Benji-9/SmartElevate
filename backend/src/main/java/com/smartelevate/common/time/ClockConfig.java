package com.smartelevate.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Único reloj de la app, en UTC. Los tests lo reemplazan por un {@code Clock.fixed(...)}. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
