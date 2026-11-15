package com.smartelevate.elevator.service;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Kioscos del QR (ADR 0008, 0020). Parámetros técnicos y de seguridad: van en {@code application.yml} con override
 * por variable de entorno (ADR 0017). La clave HMAC ({@code APP_KIOSK_QR_SECRET}) es un secreto: no tiene valor en
 * el repo; en prod la exige {@code RequiredProdProperties} (#222) y en dev/tests, si falta, se usa una efímera.
 *
 * @param qrShiftBoundaries    horas del campus (HH:mm) en que cambian los turnos de clase; el token del QR vale hasta
 *                             la próxima (ADR 0026). Default: mañana 07-13, tarde 13-18, noche 18-23
 * @param simulatorEnabled     habilita el alta de kioscos simulados (simulador de admin, #232)
 * @param rateLimitMaxRequests pedidos de token por kiosco en {@code rateLimitWindow}; se espera ~1 por turno
 */
@Validated
@ConfigurationProperties(prefix = "app.kiosk")
public record KioskProperties(
        String qrSecret,
        @DefaultValue({"07:00", "13:00", "18:00", "23:00"}) @NotNull List<String> qrShiftBoundaries,
        @DefaultValue("false") boolean simulatorEnabled,
        @DefaultValue("10") @Min(1) int rateLimitMaxRequests,
        @DefaultValue("PT1M") @NotNull Duration rateLimitWindow) {

    public KioskProperties {
        if (qrShiftBoundaries != null) {
            if (qrShiftBoundaries.isEmpty()) {
                throw new IllegalArgumentException("app.kiosk.qr-shift-boundaries necesita al menos una hora");
            }
            try {
                qrShiftBoundaries.forEach(LocalTime::parse);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("app.kiosk.qr-shift-boundaries: horas en formato HH:mm", e);
            }
        }
        if (rateLimitWindow != null && !rateLimitWindow.isPositive()) {
            throw new IllegalArgumentException("app.kiosk.rate-limit-window tiene que ser mayor a 0");
        }
    }
}
