package com.smartelevate.user.service;

import java.time.Duration;
import lombok.Getter;

/** Se pidió otro mail antes de que pase la espera mínima. */
@Getter
public class ResendTooSoonException extends RuntimeException {

    private final Duration retryAfter;

    public ResendTooSoonException(Duration retryAfter) {
        super("Ya te mandamos un mail hace poco. Probá de nuevo en " + retryAfter.toSeconds() + " segundos.");
        this.retryAfter = retryAfter;
    }
}
