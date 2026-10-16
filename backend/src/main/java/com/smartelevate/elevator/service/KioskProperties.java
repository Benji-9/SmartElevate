package com.smartelevate.elevator.service;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Kioscos del QR (ADR 0016, 0020). Parámetros técnicos y de seguridad: van en {@code application.yml} con override
 * por variable de entorno (ADR 0017). La clave HMAC ({@code APP_KIOSK_QR_SECRET}) es un secreto: no tiene valor en
 * el repo; en prod la exige {@code RequiredProdProperties} (#222) y en dev/tests, si falta, se usa una efímera.
 *
 * @param qrTtl                vida de cada token del QR, en segundos enteros; se acepta además el ciclo anterior
 * @param simulatorEnabled     habilita el alta de kioscos simulados (simulador de admin, #232)
 * @param rateLimitMaxRequests pedidos de token por kiosco en {@code rateLimitWindow}; se espera ~1 cada 30 s
 */
@Validated
@ConfigurationProperties(prefix = "app.kiosk")
public record KioskProperties(
        String qrSecret,
        @DefaultValue("PT30S") @NotNull Duration qrTtl,
        @DefaultValue("false") boolean simulatorEnabled,
        @DefaultValue("10") @Min(1) int rateLimitMaxRequests,
        @DefaultValue("PT1M") @NotNull Duration rateLimitWindow) {

    public KioskProperties {
        if (qrTtl != null && qrTtl.toSeconds() < 1) {
            throw new IllegalArgumentException("app.kiosk.qr-ttl tiene que ser de al menos 1 segundo");
        }
        if (rateLimitWindow != null && !rateLimitWindow.isPositive()) {
            throw new IllegalArgumentException("app.kiosk.rate-limit-window tiene que ser mayor a 0");
        }
    }
}
