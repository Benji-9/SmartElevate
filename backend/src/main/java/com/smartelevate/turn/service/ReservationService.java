package com.smartelevate.turn.service;

import static com.smartelevate.turn.service.ReservationRejectedException.Reason.ACTIVE_LIMIT_REACHED;
import static com.smartelevate.turn.service.ReservationRejectedException.Reason.DAILY_LIMIT_REACHED;
import static com.smartelevate.turn.service.ReservationRejectedException.Reason.DEPARTURE_NOT_FOUND;
import static com.smartelevate.turn.service.ReservationRejectedException.Reason.ELEVATOR_NOT_BOOKABLE;
import static com.smartelevate.turn.service.ReservationRejectedException.Reason.FLOOR_NOT_SERVED;
import static com.smartelevate.turn.service.ReservationRejectedException.Reason.FLOOR_OUT_OF_RANGE;
import static com.smartelevate.turn.service.ReservationRejectedException.Reason.LOW_FLOOR_DESTINATION;
import static com.smartelevate.turn.service.ReservationRejectedException.Reason.OUTSIDE_BOOKING_WINDOW;
import static com.smartelevate.turn.service.ReservationRejectedException.Reason.SAME_FLOOR;

import com.smartelevate.common.config.ConfigService;
import com.smartelevate.common.time.CampusTime;
import com.smartelevate.elevator.model.Building;
import com.smartelevate.elevator.model.Elevator;
import com.smartelevate.elevator.model.ElevatorUsage;
import com.smartelevate.turn.dto.ReservationResponse;
import com.smartelevate.turn.dto.ReserveRequest;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.DepartureStatus;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.turn.repository.DepartureRepository;
import com.smartelevate.turn.repository.ReservationRepository;
import com.smartelevate.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserva de un lugar en una salida (ADR 0007 y 0010).
 *
 * <p><b>Cupo atómico:</b> se eligió el <b>lock pesimista</b> sobre la fila de la salida ({@code SELECT ... FOR UPDATE})
 * con el constraint único (salida + lugar) como red de seguridad. El lock deja el conteo y la asignación del lugar
 * en una sola sección crítica por salida, y el perdedor recibe un {@link TurnFullException} limpio (409) en vez de
 * un error de constraint que habría que reintentar. Los límites por usuario se protegen bloqueando la fila del
 * usuario, así dos requests simultáneos del mismo usuario no pasan los dos. Orden de locks: usuario, después salida.
 *
 * <p>Pendiente de #117: los lugares reservados para prioritarios y el ascensor dedicado. Hoy una salida admite
 * hasta {@code turn_capacity} reservas comunes y los dedicados no se pueden reservar. La exención de movilidad
 * reducida a la regla de pisos bajos también llega con #117: por ahora rige para todos.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservations;
    private final DepartureRepository departures;
    private final UserRepository users;
    private final ConfigService config;
    private final CampusTime time;

    @Transactional
    public ReservationResponse reserve(UUID userId, ReserveRequest request) {
        users.lockById(userId).orElseThrow(() -> new IllegalStateException("Usuario inexistente: " + userId));
        Departure departure = departures.findByIdForUpdate(request.departureId())
                .orElseThrow(() -> new ReservationRejectedException(DEPARTURE_NOT_FOUND, "La salida no existe."));
        Instant now = time.now();

        checkDeparture(departure, now);
        checkFloors(departure.getElevator(), request.originFloor(), request.destinationFloor());
        checkUserLimits(userId, now);

        Set<Integer> taken = new HashSet<>(reservations.activeSeats(departure.getId()));
        int seat = 1;
        while (taken.contains(seat)) {
            seat++;
        }
        if (seat > departure.getCapacity()) {
            throw new TurnFullException();
        }
        Reservation saved = reservations.saveAndFlush(
                new Reservation(userId, departure, seat, request.originFloor(), request.destinationFloor(), now));
        return new ReservationResponse(saved.getId(), departure.getId(), departure.getElevator().getCode(),
                departure.getDepartureAt(), seat, saved.getOriginFloor(), saved.getDestinationFloor(),
                saved.getStatus(), saved.getCreatedAt());
    }

    private void checkDeparture(Departure departure, Instant now) {
        Elevator elevator = departure.getElevator();
        if (departure.getStatus() != DepartureStatus.SCHEDULED || !elevator.isActive()
                || elevator.getUsage() != ElevatorUsage.COMMON) {
            throw new ReservationRejectedException(ELEVATOR_NOT_BOOKABLE, "Esa salida no se puede reservar.");
        }
        Instant at = departure.getDepartureAt();
        boolean sameDay = time.toCampus(at).toLocalDate().equals(time.today());
        boolean inWindow = !at.isBefore(now.plus(config.bookingClosesBefore()))
                && !at.isAfter(now.plus(config.bookingOpensBefore()));
        if (!sameDay || !inWindow) {
            throw new ReservationRejectedException(OUTSIDE_BOOKING_WINDOW,
                    "La salida está fuera de la ventana de reserva.");
        }
    }

    private void checkFloors(Elevator elevator, int origin, int destination) {
        if (origin == destination) {
            throw new ReservationRejectedException(SAME_FLOOR, "El piso de origen y el de destino son el mismo.");
        }
        Building building = elevator.getCore().getBuilding();
        if (!inRange(building, origin) || !inRange(building, destination)) {
            throw new ReservationRejectedException(FLOOR_OUT_OF_RANGE, "Alguno de los pisos no existe en el edificio.");
        }
        if (!elevator.getFloors().contains(origin) || !elevator.getFloors().contains(destination)) {
            throw new ReservationRejectedException(FLOOR_NOT_SERVED, "Ese ascensor no para en alguno de los pisos.");
        }
        int lowFloors = config.lowFloorsThreshold();
        if (destination >= 1 && destination <= lowFloors) {
            throw new ReservationRejectedException(LOW_FLOOR_DESTINATION,
                    "Para ir a los pisos 1 a %d usá la escalera.".formatted(lowFloors));
        }
    }

    private static boolean inRange(Building building, int floor) {
        return floor >= building.getMinFloor() && floor <= building.getMaxFloor();
    }

    private void checkUserLimits(UUID userId, Instant now) {
        if (reservations.countOngoing(userId, now) >= config.maxActiveBookingsPerUser()) {
            throw new ReservationRejectedException(ACTIVE_LIMIT_REACHED, "Ya tenés una reserva activa.");
        }
        LocalDate today = time.today();
        Instant dayStart = time.toInstant(today, LocalTime.MIDNIGHT);
        Instant dayEnd = time.toInstant(today.plusDays(1), LocalTime.MIDNIGHT);
        if (reservations.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(userId, dayStart, dayEnd)
                >= config.maxDailyBookingsPerUser()) {
            throw new ReservationRejectedException(DAILY_LIMIT_REACHED, "Llegaste al tope de reservas de hoy.");
        }
    }
}
