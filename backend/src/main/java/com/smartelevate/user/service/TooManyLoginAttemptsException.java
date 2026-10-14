package com.smartelevate.user.service;

import java.time.Duration;
import lombok.Getter;

/** Demasiados logins fallidos para la cuenta o la IP en la ventana de {@code app_config}. */
@Getter
public class TooManyLoginAttemptsException extends RuntimeException {

    /** Segundos enteros para el header {@code Retry-After}, redondeados hacia arriba y al menos 1. */
    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(Duration retryAfter) {
        this(Math.max(1, retryAfter.plusNanos(999_999_999).toSeconds()));
    }

    private TooManyLoginAttemptsException(long seconds) {
        super("Hubo demasiados intentos fallidos. Probá de nuevo en " + minutes(seconds) + ".");
        this.retryAfterSeconds = seconds;
    }

    private static String minutes(long seconds) {
        long minutes = Math.ceilDiv(seconds, 60);
        return minutes == 1 ? "1 minuto" : minutes + " minutos";
    }
}
