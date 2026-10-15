package com.smartelevate.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita los jobs {@code @Scheduled} (hoy, el de no-shows). Se apaga con {@code app.scheduling.enabled=false}
 * ({@code APP_SCHEDULING_ENABLED}): los tests lo apagan e invocan los jobs a mano con un {@code Clock.fixed}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
