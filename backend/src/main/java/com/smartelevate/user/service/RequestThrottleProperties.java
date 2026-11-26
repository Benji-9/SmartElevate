package com.smartelevate.user.service;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Tope de pedidos por IP de registro, reenvío de verificación y "olvidé mi contraseña" (#344). Parámetro de
 * seguridad: {@code application.yml} con override por variable de entorno (ADR 0017).
 */
@Validated
@ConfigurationProperties(prefix = "app.request-throttle")
public record RequestThrottleProperties(
        @DefaultValue("PT15M") @NotNull Duration window,
        @DefaultValue("60") @Min(1) int maxPerIp) {

    public RequestThrottleProperties {
        if (window != null && !window.isPositive()) {
            throw new IllegalArgumentException("app.request-throttle.window tiene que ser mayor a 0");
        }
    }
}
