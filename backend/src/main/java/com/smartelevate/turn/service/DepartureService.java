package com.smartelevate.turn.service;

import com.smartelevate.common.config.ConfigService;
import com.smartelevate.common.time.CampusTime;
import com.smartelevate.elevator.model.Elevator;
import com.smartelevate.elevator.model.ElevatorUsage;
import com.smartelevate.elevator.repository.ElevatorRepository;
import com.smartelevate.turn.dto.DepartureResponse;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.DepartureStatus;
import com.smartelevate.turn.repository.DepartureRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Salidas de ascensor (ADR 0007). Se generan perezosamente al consultar la ventana de reserva y de forma
 * idempotente: solo se insertan las que faltan (y el constraint único ascensor + instante lo garantiza).
 */
@Service
@RequiredArgsConstructor
public class DepartureService {

    private final DepartureRepository departures;
    private final ElevatorRepository elevators;
    private final ConfigService config;
    private final CampusTime time;

    /**
     * Salidas reservables ahora (entre apertura y cierre de la ventana, del día de hoy) de los ascensores comunes
     * activos, filtrando por ascensor y/o edificio (null = sin filtro). Los dedicados quedan fuera (#117).
     */
    // ponytail: dos consultas concurrentes pueden chocar con el constraint único (una falla con 500);
    // reintentar o insertar con ON CONFLICT si llega a pasar en producción.
    @Transactional
    public List<DepartureResponse> findBookable(String elevatorCode, String buildingCode) {
        List<Elevator> candidates = elevators.findAll().stream()
                .filter(e -> e.isActive() && e.getUsage() == ElevatorUsage.COMMON)
                .filter(e -> elevatorCode == null || e.getCode().equals(elevatorCode))
                .filter(e -> buildingCode == null
                        || e.getCore().getBuilding().getCode().equalsIgnoreCase(buildingCode))
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        Instant now = time.now();
        Instant from = now.plus(config.bookingClosesBefore());
        Instant to = now.plus(config.bookingOpensBefore());
        generate(candidates, from, to);
        return departures
                .findByElevatorInAndStatusAndDepartureAtBetweenOrderByDepartureAtAscElevatorCodeAsc(
                        candidates, DepartureStatus.SCHEDULED, from, to)
                .stream()
                .map(d -> new DepartureResponse(d.getId(), d.getElevator().getCode(),
                        d.getElevator().getCore().getBuilding().getCode(), d.getDepartureAt(), d.getCapacity(),
                        // Sin reservas todavía (llegan con #7x): el cupo restante es la capacidad.
                        d.getCapacity(), d.getStatus()))
                .toList();
    }

    /** Crea las salidas que faltan en [from, to], dentro del horario de operación del día de {@code from}. */
    void generate(List<Elevator> targets, Instant from, Instant to) {
        LocalDate day = time.toCampus(from).toLocalDate();
        Instant end = time.toInstant(day, config.operatingHoursEnd());
        Duration step = config.slotDuration();
        List<Instant> slots = new ArrayList<>();
        // La salida de las 22:30 no se genera: el horario de operación es [inicio, fin).
        for (Instant t = time.toInstant(day, config.operatingHoursStart()); t.isBefore(end); t = t.plus(step)) {
            if (!t.isBefore(from) && !t.isAfter(to)) {
                slots.add(t);
            }
        }
        if (slots.isEmpty()) {
            return;
        }
        Set<String> existing = new HashSet<>();
        departures.findByElevatorInAndStatusAndDepartureAtBetweenOrderByDepartureAtAscElevatorCodeAsc(
                targets, DepartureStatus.SCHEDULED, slots.getFirst(), slots.getLast())
                .forEach(d -> existing.add(key(d.getElevator().getCode(), d.getDepartureAt())));
        int capacity = config.turnCapacity();
        List<Departure> missing = new ArrayList<>();
        for (Elevator e : targets) {
            for (Instant t : slots) {
                if (!existing.contains(key(e.getCode(), t))) {
                    missing.add(new Departure(e, t, capacity));
                }
            }
        }
        departures.saveAll(missing);
    }

    private static String key(String elevatorCode, Instant at) {
        return elevatorCode + "@" + at;
    }
}
