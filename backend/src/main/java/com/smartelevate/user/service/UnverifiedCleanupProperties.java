package com.smartelevate.user.service;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Limpieza de cuentas que nunca verificaron el email (#344). Parámetros técnicos: {@code application.yml} con override
 * por variable de entorno, no {@code app_config} (ADR 0017). Se validan al arrancar: con valores fuera de rango, la app
 * no levanta.
 *
 * @param ttl      desde cuándo una cuenta sin verificar se considera abandonada y se borra
 * @param interval cada cuánto corre el job (también es la espera antes de la primera pasada)
 */
@Validated
@ConfigurationProperties(prefix = "app.unverified-cleanup")
public record UnverifiedCleanupProperties(
        @DefaultValue("P7D") @NotNull Duration ttl,
        @DefaultValue("PT1H") @NotNull Duration interval) {

    public UnverifiedCleanupProperties {
        if (ttl != null && !ttl.isPositive()) {
            throw new IllegalArgumentException("app.unverified-cleanup.ttl tiene que ser mayor a 0");
        }
        if (interval != null && !interval.isPositive()) {
            throw new IllegalArgumentException("app.unverified-cleanup.interval tiene que ser mayor a 0");
        }
    }
}
