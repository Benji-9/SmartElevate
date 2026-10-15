package com.smartelevate.turn.service;

import com.smartelevate.common.time.CampusTime;
import com.smartelevate.elevator.model.Elevator;
import com.smartelevate.elevator.repository.ElevatorRepository;
import com.smartelevate.elevator.service.KioskException;
import com.smartelevate.priority.model.PriorityAudit;
import com.smartelevate.priority.repository.PriorityAuditRepository;
import com.smartelevate.turn.dto.AdminElevatorResponse;
import com.smartelevate.turn.dto.ElevatorServiceResponse;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.turn.repository.DepartureRepository;
import com.smartelevate.turn.repository.ReservationRepository;
import com.smartelevate.turn.service.TurnMailService.OutOfServiceNotice;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ascensor fuera de servicio por avería (turnos.md §8, #134).
 *
 * <p><b>Marcar:</b> en una sola transacción (todas las reservas o ninguna) se bloquea el ascensor, se bloquean sus
 * salidas futuras (por id) y recién después se leen las reservas ACTIVE: una reserva que estaba entrando termina antes
 * y entra en la lectura. Las que llegan después ven {@code active = false} y se rechazan (ELEVATOR_NOT_BOOKABLE).
 * Las reservas pasan a CANCELLED (sin falta, devuelve el cupo diario) y se publica {@link OutageCancellations}, que
 * manda los mails recién después del commit ({@link OutOfServiceMailListener}). No se toman locks de usuario: este
 * servicio no espera nada más una vez que tiene salidas bloqueadas, así que no cierra un ciclo con reservar/cancelar
 * (usuario, después salida).
 *
 * <p>Las salidas SCHEDULED no se tocan: dejan de ofrecerse y de generarse porque {@code DepartureService} solo
 * considera ascensores activos, y {@code reserve} rechaza los inactivos. Al reactivar vuelven solas.
 * Reservas USED, NO_SHOW y de salidas que ya pasaron no se tocan.
 */
@Service
@RequiredArgsConstructor
public class ElevatorOutageService {

    static final String OUT_OF_SERVICE = "ELEVATOR_OUT_OF_SERVICE";
    static final String IN_SERVICE = "ELEVATOR_IN_SERVICE";

    private final ElevatorRepository elevators;
    private final DepartureRepository departures;
    private final ReservationRepository reservations;
    private final PriorityAuditRepository audit;
    private final CampusTime time;
    private final ApplicationEventPublisher events;

    /** Datos planos de los avisos, armados antes del commit. */
    public record OutageCancellations(List<OutOfServiceNotice> notices) {
    }

    @Transactional(readOnly = true)
    public List<AdminElevatorResponse> list() {
        Map<String, Integer> upcoming = new HashMap<>();
        reservations.countActiveFromByElevator(time.now())
                .forEach(row -> upcoming.put((String) row[0], ((Number) row[1]).intValue()));
        return elevators.findAll().stream()
                .sorted(Comparator.comparing((Elevator e) -> e.getCore().getBuilding().getCode())
                        .thenComparing(e -> e.getCore().getCode()).thenComparing(Elevator::getCode))
                .map(e -> new AdminElevatorResponse(e.getCode(), e.getCore().getName(),
                        e.getCore().getBuilding().getCode(), e.getUsage(), e.isActive(),
                        upcoming.getOrDefault(e.getCode(), 0)))
                .toList();
    }

    /** Marcarlo dos veces no hace nada (ni cancela ni audita de nuevo). */
    @Transactional
    public ElevatorServiceResponse markOutOfService(UUID actorId, String code, String reason) {
        Elevator elevator = lock(code);
        if (!elevator.isActive()) {
            return new ElevatorServiceResponse(code, false, 0);
        }
        Instant now = time.now();
        elevator.setActive(false);
        departures.lockFutureByElevator(code, now);
        List<Reservation> affected = reservations.findActiveByElevatorFrom(code, now);
        List<OutOfServiceNotice> notices = affected.stream()
                .map(r -> new OutOfServiceNotice(r.getUserId(), r.getDeparture().getDepartureAt(), code,
                        elevator.getCore().getName(), elevator.getCore().getBuilding().getName()))
                .toList();
        affected.forEach(Reservation::cancelByOutage);
        audit.save(PriorityAudit.forElevator(actorId, code, OUT_OF_SERVICE, blankToNull(reason), now));
        if (!notices.isEmpty()) {
            events.publishEvent(new OutageCancellations(notices));
        }
        return new ElevatorServiceResponse(code, false, affected.size());
    }

    @Transactional
    public ElevatorServiceResponse markInService(UUID actorId, String code) {
        Elevator elevator = lock(code);
        if (!elevator.isActive()) {
            elevator.setActive(true);
            audit.save(PriorityAudit.forElevator(actorId, code, IN_SERVICE, null, time.now()));
        }
        return new ElevatorServiceResponse(code, true, 0);
    }

    private Elevator lock(String code) {
        return elevators.findByCodeForUpdate(code)
                .orElseThrow(() -> new KioskException(KioskException.Reason.ELEVATOR_NOT_FOUND, "Ascensor inexistente"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
