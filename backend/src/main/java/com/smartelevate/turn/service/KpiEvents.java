package com.smartelevate.turn.service;

import com.smartelevate.turn.model.CheckIn;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.turn.repository.CheckInRepository;
import com.smartelevate.turn.repository.ReservationRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Único punto de consulta</b> de check-ins y no-shows para los KPIs (kpis.md) y las estadísticas del piloto
 * (#220): deja afuera lo que vino del simulador de admin (los check-ins {@code SIMULATED} y las reservas que tienen
 * uno). Los KPIs no leen {@code check_in} ni {@code reservation} por otro lado. Períodos {@code [from, to)} en UTC.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KpiEvents {

    private final CheckInRepository checkIns;
    private final ReservationRepository reservations;

    /** Check-ins de kioscos reales, por momento del escaneo. */
    public List<CheckIn> checkIns(Instant from, Instant to) {
        return checkIns.findRealBetween(from, to);
    }

    /** Reservas NO_SHOW por hora de salida, sin las que tienen un check-in simulado (un LATE del simulador). */
    public List<Reservation> noShows(Instant from, Instant to) {
        return reservations.findRealNoShowsBetween(from, to);
    }

    /**
     * Reservas no canceladas (ACTIVE, USED, NO_SHOW) por hora de salida, sin las que tienen un check-in simulado.
     * USED es el check-in cumplido (a tiempo, en su ascensor o en otro).
     */
    public List<Reservation> reservations(Instant from, Instant to) {
        return reservations.findRealNotCancelledBetween(from, to);
    }
}
