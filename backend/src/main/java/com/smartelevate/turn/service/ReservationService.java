package com.smartelevate.turn.service;

import static com.smartelevate.turn.service.ReservationRejectedException.Reason.ACTIVE_LIMIT_REACHED;
import static com.smartelevate.turn.service.ReservationRejectedException.Reason.DAILY_LIMIT_REACHED;
import static com.smartelevate.turn.service.ReservationRejectedException.Reason.DEPARTURE_NOT_FOUND;
import static com.smartelevate.turn.service.ReservationRejectedException.Reason.ELEVATOR_NOT_BOOKABLE;
import static com.smartelevate.turn.service.ReservationRejectedException.Reason.FLOOR_NOT_SERVED;
import static com.smartelevate.turn.service.ReservationRejectedException.Reason.FLOOR_OUT_OF_RANGE;
import static com.smartelevate.turn.service.ReservationRejectedException.Reason.LOW_FLOOR_DESTINATION;
import static com.smartelevate.turn.service.ReservationRejectedException.Reason.OUTSIDE_BOOKING_WINDOW;
import static com.smartelevate.turn.service.ReservationRejectedException.Reason.RESERVATION_NOT_FOUND;
import static com.smartelevate.turn.service.ReservationRejectedException.Reason.SAME_FLOOR;

import com.smartelevate.common.config.ConfigService;
import com.smartelevate.common.time.CampusTime;
import com.smartelevate.elevator.model.Building;
import com.smartelevate.elevator.model.Elevator;
import com.smartelevate.priority.model.PriorityCategory;
import com.smartelevate.priority.service.PriorityService;
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
import java.util.List;
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
 * <p><b>Prioridad (#117):</b> se consulta a {@link PriorityService#currentPriority} dentro de la misma transacción,
 * nunca a un claim del JWT (ADR 0009). Decide el ascensor dedicado, la exención de pisos bajos para movilidad
 * reducida y los lugares reservados a prioritarios ({@link PrioritySeats}), que se cuentan bajo el mismo lock.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservations;
    private final DepartureRepository departures;
    private final UserRepository users;
    private final ConfigService config;
    private final CampusTime time;
    private final PriorityService priorities;

    @Transactional
    public ReservationResponse reserve(UUID userId, ReserveRequest request) {
        users.lockById(userId).orElseThrow(() -> new IllegalStateException("Usuario inexistente: " + userId));
        Departure departure = departures.findByIdForUpdate(request.departureId())
                .orElseThrow(() -> new ReservationRejectedException(DEPARTURE_NOT_FOUND, "La salida no existe."));
        Instant now = time.now();
        PriorityCategory priority = priorities.currentPriority(userId);
        boolean priorityUser = priority != PriorityCategory.NONE;

        checkDeparture(departure, now, priority);
        checkFloors(departure.getElevator(), request.originFloor(), request.destinationFloor(), priority);
        checkUserLimits(userId, now);

        Set<Integer> taken = new HashSet<>(reservations.activeSeats(departure.getId()));
        if (taken.size() >= departure.getCapacity()) {
            throw new TurnFullException();
        }
        int held = PrioritySeats.held(departure, now, config);
        if (!priorityUser && held > 0 && PrioritySeats.remaining(departure.getCapacity(), taken.size(),
                reservations.countActivePriority(departure.getId()), held, false) == 0) {
            throw new TurnFullException("Los lugares que quedan están reservados para prioritarios. Elegí otra salida.");
        }
        int seat = 1;
        while (taken.contains(seat)) {
            seat++;
        }
        return toResponse(reservations.saveAndFlush(new Reservation(userId, departure, seat, request.originFloor(),
                request.destinationFloor(), now, priorityUser)));
    }

    /**
     * Cancela una reserva del usuario y libera su lugar (ver {@link Reservation#cancel}). Idempotente.
     * Toma los mismos locks que {@link #reserve}, en el mismo orden (usuario, después salida): serializa con las
     * reservas de esa salida y con otro pedido del mismo usuario, sin riesgo de deadlock.
     * Una reserva ajena responde igual que una inexistente (404), para no filtrar que existe.
     *
     * <p>Pendiente: la excepción de espera real mayor a la estimada + X (necesita el check-in) y el registro de la
     * falta y la suspensión (épica de check-in). Cancelar no devuelve el cupo diario (ver {@link #checkUserLimits}).
     */
    @Transactional
    public void cancel(UUID userId, long reservationId) {
        users.lockById(userId).orElseThrow(() -> new IllegalStateException("Usuario inexistente: " + userId));
        Reservation reservation = reservations.findById(reservationId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> new ReservationRejectedException(RESERVATION_NOT_FOUND, "La reserva no existe."));
        departures.findByIdForUpdate(reservation.getDeparture().getId());
        reservation.cancel(time.now(), config.cancellationDeadlineBefore());
    }

    /** Reservas del usuario (activas y canceladas) con salida desde el inicio de hoy, la más tardía primero. */
    @Transactional(readOnly = true)
    public List<ReservationResponse> mine(UUID userId) {
        return reservations.findMine(userId, time.toInstant(time.today(), LocalTime.MIDNIGHT)).stream()
                .map(ReservationService::toResponse)
                .toList();
    }

    private static ReservationResponse toResponse(Reservation r) {
        Departure d = r.getDeparture();
        return new ReservationResponse(r.getId(), d.getId(), d.getElevator().getCode(), d.getDepartureAt(),
                r.getSeatNumber(), r.getOriginFloor(), r.getDestinationFloor(), r.getStatus(), r.getCreatedAt());
    }

    private void checkDeparture(Departure departure, Instant now, PriorityCategory priority) {
        Elevator elevator = departure.getElevator();
        if (departure.getStatus() != DepartureStatus.SCHEDULED || !elevator.isActive()) {
            throw new ReservationRejectedException(ELEVATOR_NOT_BOOKABLE, "Esa salida no se puede reservar.");
        }
        if (!elevator.getUsage().allows(priority)) {
            throw new ReservationRejectedException(ELEVATOR_NOT_BOOKABLE, switch (elevator.getUsage()) {
                case TEACHERS -> "Ese ascensor es exclusivo para docentes.";
                case REDUCED_MOBILITY -> "Ese ascensor es exclusivo para personas con movilidad reducida.";
                case COMMON -> throw new IllegalStateException("Un ascensor común admite a todos");
            });
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

    private void checkFloors(Elevator elevator, int origin, int destination, PriorityCategory priority) {
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
        // Movilidad reducida queda exenta: no puede usar la escalera (asignacion.md#elegibilidad).
        int lowFloors = config.lowFloorsThreshold();
        if (priority != PriorityCategory.REDUCED_MOBILITY && destination >= 1 && destination <= lowFloors) {
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
