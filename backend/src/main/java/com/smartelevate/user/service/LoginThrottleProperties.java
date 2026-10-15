package com.smartelevate.user.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Límite de logins fallidos (ADR 0015). Parámetro de seguridad: va en {@code application.yml} con override por
 * variable de entorno, no en {@code app_config} (#225). Se valida al arrancar: fuera de rango, la app no levanta.
 */
@Validated
@ConfigurationProperties(prefix = "app.login-throttle")
public record LoginThrottleProperties(
        @DefaultValue("PT15M") @NotNull Duration window,
        // NIST SP 800-63B: como mucho 100 fallos seguidos por cuenta.
        @DefaultValue("10") @Min(1) @Max(100) int maxFailuresPerAccount,
        @DefaultValue("100") @Min(1) int maxFailuresPerIp) {

    public LoginThrottleProperties {
        if (window != null && !window.isPositive()) {
            throw new IllegalArgumentException("app.login-throttle.window tiene que ser mayor a 0");
        }
    }
}
