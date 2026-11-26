package com.smartelevate.user.service;

import java.time.Duration;
import lombok.Getter;

/** Demasiados pedidos de la misma acción desde una IP en la ventana de {@link RequestThrottleProperties}. */
@Getter
public class TooManyRequestsException extends RuntimeException {

    /** Segundos enteros para el header {@code Retry-After}, redondeados hacia arriba y al menos 1. */
    private final long retryAfterSeconds;

    public TooManyRequestsException(Duration retryAfter) {
        this(Math.max(1, retryAfter.plusNanos(999_999_999).toSeconds()));
    }

    private TooManyRequestsException(long seconds) {
        super("Hubo demasiados pedidos desde tu red. Probá de nuevo en " + minutes(seconds) + ".");
        this.retryAfterSeconds = seconds;
    }

    private static String minutes(long seconds) {
        long minutes = Math.ceilDiv(seconds, 60);
        return minutes == 1 ? "1 minuto" : minutes + " minutos";
    }
}
