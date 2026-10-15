package com.smartelevate.turn.service;

import static com.smartelevate.turn.service.ReservationRejectedException.Reason.ALREADY_CHECKED_IN;
import static com.smartelevate.turn.service.ReservationRejectedException.Reason.NO_ACTIVE_RESERVATION;
import static com.smartelevate.turn.service.ReservationRejectedException.Reason.OUTSIDE_CHECK_IN_WINDOW;

import com.smartelevate.common.config.ConfigService;
import com.smartelevate.common.time.CampusTime;
import com.smartelevate.elevator.service.QrTokenService;
import com.smartelevate.elevator.service.QrTokenService.QrTokenClaims;
import com.smartelevate.priority.model.PriorityAudit;
import com.smartelevate.priority.repository.PriorityAuditRepository;
import com.smartelevate.turn.dto.CheckInResponse;
import com.smartelevate.turn.model.CheckIn;
import com.smartelevate.turn.model.CheckInOutcome;
import com.smartelevate.turn.model.CheckInSource;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.turn.model.ReservationStatus;
import com.smartelevate.turn.repository.CheckInRepository;
import com.smartelevate.turn.repository.ReservationRepository;
import com.smartelevate.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Check-in con el QR del ascensor (ADR 0008 y 0020, check-in-qr.md). El JWT dice quién escanea; el token firmado,
 * el ascensor y el kiosco (y si es simulado). Ventana: de {@code check_in_opens_before} antes a
 * {@code check_in_closes_after} después de la salida; pasado {@code no_show_after} el resultado es LATE.
 *
 * <p><b>Una lectura por reserva:</b> se bloquea la fila del usuario (el mismo primer lock que reservar y cancelar),
 * así dos escaneos simultáneos se serializan y el segundo ve el check-in del primero. El único de
 * {@code check_in.reservation_id} es la red de seguridad.
 *
 * <p><b>LATE y el job de no-shows (#231):</b> un escaneo LATE deja la reserva NO_SHOW aunque el job todavía no haya
 * corrido; si ya corrió, queda igual. El job tiene que marcar con un update condicional
 * ({@code status = 'ACTIVE'}): así no pisa un USED de un escaneo a tiempo que se le adelantó.
 */
@Service
@RequiredArgsConstructor
public class CheckInService {

    /**
     * Las que se buscan en la ventana: activa, o ya marcada no-show por el job (el escaneo LATE llega después).
     * USED entra para responder ALREADY_CHECKED_IN en vez de "sin turno".
     */
    private static final Set<ReservationStatus> CHECKABLE =
            EnumSet.of(ReservationStatus.ACTIVE, ReservationStatus.NO_SHOW, ReservationStatus.USED);

    private final QrTokenService qrTokens;
    private final ReservationRepository reservations;
    private final CheckInRepository checkIns;
    private final UserRepository users;
    private final PriorityAuditRepository audit;
    private final ConfigService config;
    private final CampusTime time;

    @Transactional
    public CheckInResponse checkIn(UUID userId, String token) {
        QrTokenClaims qr = qrTokens.validate(token);
        users.lockById(userId).orElseThrow(() -> new IllegalStateException("Usuario inexistente: " + userId));
        Instant now = time.now();

        // Las de hoy, la salida más tardía primero: si dos caen en la ventana, gana la próxima.
        List<Reservation> mine = reservations.findMine(userId, time.toInstant(time.today(), LocalTime.MIDNIGHT));
        Reservation reservation = mine.stream()
                .filter(r -> CHECKABLE.contains(r.getStatus()) && inWindow(r, now))
                .findFirst()
                .orElseThrow(() -> mine.stream().anyMatch(r -> r.getStatus() == ReservationStatus.ACTIVE)
                        ? new ReservationRejectedException(OUTSIDE_CHECK_IN_WINDOW,
                                "Tu turno no está en horario de check-in.")
                        : new ReservationRejectedException(NO_ACTIVE_RESERVATION,
                                "No tenés un turno para hacer check-in."));
        if (reservation.getStatus() == ReservationStatus.USED || checkIns.existsByReservationId(reservation.getId())) {
            throw new ReservationRejectedException(ALREADY_CHECKED_IN, "Ya registraste el check-in de este turno.");
        }

        String reserved = reservation.getDeparture().getElevator().getCode();
        CheckInOutcome outcome = now.isAfter(reservation.getDeparture().getDepartureAt().plus(config.noShowAfter()))
                ? CheckInOutcome.LATE
                : reserved.equals(qr.elevatorId()) ? CheckInOutcome.ON_TIME : CheckInOutcome.OTHER_ELEVATOR;
        if (outcome == CheckInOutcome.LATE) {
            reservation.markNoShow();
        } else {
            reservation.markUsed();
        }
        CheckInSource source = qr.simulated() ? CheckInSource.SIMULATED : CheckInSource.REAL;
        checkIns.saveAndFlush(new CheckIn(reservation.getId(), outcome, qr.elevatorId(), source, qr.kioskId(), now));
        if (qr.simulated()) {
            audit.save(PriorityAudit.forKiosk(userId, qr.kioskId(), "CHECK_IN_SIMULATED",
                    "reservation " + reservation.getId() + " " + outcome, now));
        }
        return new CheckInResponse(reservation.getId(), outcome, reservation.getStatus(), qr.elevatorId(), reserved,
                reservation.getDeparture().getDepartureAt(), now, source);
    }

    private boolean inWindow(Reservation r, Instant now) {
        Instant at = r.getDeparture().getDepartureAt();
        return !now.isBefore(at.minus(config.checkInOpensBefore()))
                && !now.isAfter(at.plus(config.checkInClosesAfter()));
    }
}
