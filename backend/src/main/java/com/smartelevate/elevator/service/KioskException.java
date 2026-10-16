package com.smartelevate.elevator.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Operación de admin sobre kioscos que no se puede hacer. El {@link Reason} viaja como {@code code} en el ApiError. */
@Getter
public class KioskException extends RuntimeException {

    public enum Reason {
        KIOSK_NOT_FOUND(HttpStatus.NOT_FOUND),
        ELEVATOR_NOT_FOUND(HttpStatus.NOT_FOUND),
        SIMULATOR_DISABLED(HttpStatus.CONFLICT);

        private final HttpStatus status;

        Reason(HttpStatus status) {
            this.status = status;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }

    private final Reason reason;

    public KioskException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }
}
