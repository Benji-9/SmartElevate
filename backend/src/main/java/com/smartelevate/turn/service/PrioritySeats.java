package com.smartelevate.turn.service;

import com.smartelevate.common.config.ConfigService;
import com.smartelevate.elevator.model.ElevatorUsage;
import com.smartelevate.turn.model.Departure;
import java.time.Instant;

/**
 * Lugares reservados para prioritarios (turnos.md sección 6). En una salida de un ascensor común quedan
 * {@code priority_seats_per_departure} lugares que solo puede tomar un prioritario, hasta
 * {@code priority_seats_release_before} antes de la salida. Los prioritarios que ya reservaron cuentan contra ese
 * cupo: un común puede reservar si {@code comunes + max(reservados, prioritarios) < capacidad}.
 */
final class PrioritySeats {

    private PrioritySeats() {
    }

    /** Lugares que retiene la salida para prioritarios ahora: 0 en los dedicados y después de la liberación. */
    static int held(Departure departure, Instant now, ConfigService config) {
        boolean released = !now.isBefore(departure.getDepartureAt().minus(config.prioritySeatsReleaseBefore()));
        return departure.getElevator().getUsage() != ElevatorUsage.COMMON || released
                ? 0 : config.prioritySeatsPerDeparture();
    }

    /** Lugares que puede tomar este usuario. {@code booked} incluye a los prioritarios ({@code priorityBooked}). */
    static int remaining(int capacity, long booked, long priorityBooked, int held, boolean priorityUser) {
        long reservedLeft = priorityUser ? 0 : Math.max(0, held - priorityBooked);
        return (int) Math.max(0, capacity - booked - reservedLeft);
    }
}
