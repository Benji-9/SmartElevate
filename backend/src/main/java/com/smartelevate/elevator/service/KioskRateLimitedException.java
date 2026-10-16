package com.smartelevate.elevator.service;

import java.time.Duration;
import lombok.Getter;

/** El kiosco pidió más tokens de los que permite {@code app.kiosk.rate-limit-*}. */
@Getter
public class KioskRateLimitedException extends RuntimeException {

    /** Segundos enteros para el header {@code Retry-After}, redondeados hacia arriba y al menos 1. */
    private final long retryAfterSeconds;

    public KioskRateLimitedException(Duration retryAfter) {
        super("El kiosco pidió demasiados tokens. Probá de nuevo en unos segundos.");
        this.retryAfterSeconds = Math.max(1, retryAfter.plusNanos(999_999_999).toSeconds());
    }
}
