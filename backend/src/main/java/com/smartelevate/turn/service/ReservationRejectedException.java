package com.smartelevate.turn.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Una regla de reserva no se cumple. El {@link Reason} viaja como {@code code} en el ApiError. */
@Getter
public class ReservationRejectedException extends RuntimeException {

    public enum Reason {
        DEPARTURE_NOT_FOUND(HttpStatus.NOT_FOUND),
        OUTSIDE_BOOKING_WINDOW(HttpStatus.CONFLICT),
        ACTIVE_LIMIT_REACHED(HttpStatus.CONFLICT),
        DAILY_LIMIT_REACHED(HttpStatus.CONFLICT),
        ELEVATOR_NOT_BOOKABLE(HttpStatus.UNPROCESSABLE_ENTITY),
        SAME_FLOOR(HttpStatus.UNPROCESSABLE_ENTITY),
        FLOOR_OUT_OF_RANGE(HttpStatus.UNPROCESSABLE_ENTITY),
        FLOOR_NOT_SERVED(HttpStatus.UNPROCESSABLE_ENTITY),
        LOW_FLOOR_DESTINATION(HttpStatus.UNPROCESSABLE_ENTITY);

        private final HttpStatus status;

        Reason(HttpStatus status) {
            this.status = status;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }

    private final Reason reason;

    public ReservationRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }
}
