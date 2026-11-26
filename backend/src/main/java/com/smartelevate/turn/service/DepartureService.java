package com.smartelevate.turn.service;

import com.smartelevate.common.config.ConfigService;
import com.smartelevate.common.time.CampusTime;
import com.smartelevate.elevator.model.Elevator;
import com.smartelevate.elevator.model.ElevatorUsage;
import com.smartelevate.elevator.repository.ElevatorRepository;
import com.smartelevate.priority.model.PriorityCategory;
import com.smartelevate.priority.service.PriorityService;
import com.smartelevate.turn.dto.DepartureResponse;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.DepartureStatus;
import com.smartelevate.turn.repository.DepartureRepository;
import com.smartelevate.turn.repository.ReservationRepository;
import com.smartelevate.turn.service.ZoneOfferResolver.Candidate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private final ReservationRepository reservations;
    private final ElevatorRepository elevators;
    private final ConfigService config;
    private final CampusTime time;
    private final PriorityService priorities;

    /**
     * Salidas reservables ahora por este usuario (entre apertura y cierre de la ventana, del día de hoy) de los
     * ascensores activos que puede usar según su prioridad vigente, filtrando por ascensor y/o edificio
     * (null = sin filtro). Los lugares libres descuentan los reservados a prioritarios si el usuario es común (#117).
     */
    @Transactional
    public List<DepartureResponse> findBookable(UUID userId, String elevatorCode, String buildingCode) {
        PriorityCategory priority = priorities.currentPriority(userId);
        List<Elevator> targets = usable(priority)
                .filter(e -> elevatorCode == null || e.getCode().equals(elevatorCode))
                .filter(e -> buildingCode == null
                        || e.getCore().getBuilding().getCode().equalsIgnoreCase(buildingCode))
                .toList();
        return load(targets, time.now(), priority != PriorityCategory.NONE).stream().map(this::toResponse).toList();
    }

    /**
     * Oferta por zonas de pisos (ADR 0025): una salida por hora de la ventana, elegida por {@link ZoneOfferResolver}
     * entre las del núcleo, de ascensores activos que el usuario puede usar y que paran en origen y destino. Las
     * zonas se abren a {@code zone_open_to_any_before} de la salida y no se aplican a los ascensores dedicados.
     */
    @Transactional
    public List<DepartureResponse> findOffer(UUID userId, String coreCode, int origin, int destination) {
        PriorityCategory priority = priorities.currentPriority(userId);
        List<Elevator> targets = usable(priority)
                .filter(e -> e.getCore().getCode().equalsIgnoreCase(coreCode))
                .filter(e -> e.getFloors().contains(origin) && e.getFloors().contains(destination))
                .toList();
        Instant now = time.now();
        List<Candidate> loaded = load(targets, now, priority != PriorityCategory.NONE);
        if (loaded.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Integer>> destinations = reservations
                .activeDestinationsByDeparture(loaded.stream().map(c -> c.departure().getId()).toList()).stream()
                .collect(Collectors.groupingBy(row -> (Long) row[0],
                        Collectors.mapping(row -> ((Number) row[1]).intValue(), Collectors.toList())));
        Map<Instant, List<Candidate>> byHour = loaded.stream()
                .map(c -> new Candidate(c.departure(), c.remaining(), c.booked(),
                        destinations.getOrDefault(c.departure().getId(), List.of())))
                .collect(Collectors.groupingBy(c -> c.departure().getDepartureAt(), TreeMap::new,
                        Collectors.toList()));
        // ponytail: un núcleo mixto (comunes y dedicados) abriría también a los comunes; hoy no existe (IND1 es
        // solo dedicados). Separar los dedicados del resto si llega a haber uno.
        boolean dedicated = targets.stream().anyMatch(e -> e.getUsage() != ElevatorUsage.COMMON);
        Duration openBefore = config.zoneOpenToAnyBefore();
        int maxFloors = config.zoneAffinityMaxFloors();
        return byHour.entrySet().stream()
                .map(hour -> ZoneOfferResolver.pick(hour.getValue(), destination,
                        dedicated || !now.isBefore(hour.getKey().minus(openBefore)), maxFloors))
                .map(this::toResponse)
                .toList();
    }

    private Stream<Elevator> usable(PriorityCategory priority) {
        return elevators.findAll().stream().filter(e -> e.isActive() && e.getUsage().allows(priority));
    }

    /** Salidas de la ventana de los ascensores dados, con lo que puede tomar este usuario (sin destinos). */
    // ponytail: dos consultas concurrentes pueden chocar con el constraint único (una falla con 500);
    // reintentar o insertar con ON CONFLICT si llega a pasar en producción.
    private List<Candidate> load(List<Elevator> targets, Instant now, boolean priorityUser) {
        if (targets.isEmpty()) {
            return List.of();
        }
        Instant from = now.plus(config.bookingClosesBefore());
        Instant to = now.plus(config.bookingOpensBefore());
        generate(targets, from, to);
        List<Departure> found = departures
                .findByElevatorInAndStatusAndDepartureAtBetweenOrderByDepartureAtAscElevatorCodeAsc(
                        targets, DepartureStatus.SCHEDULED, from, to);
        Map<Long, long[]> booked = found.isEmpty() ? Map.of() : reservations
                .countActiveByDeparture(found.stream().map(Departure::getId).toList()).stream()
                .collect(Collectors.toMap(row -> (Long) row[0],
                        row -> new long[] {((Number) row[1]).longValue(), ((Number) row[2]).longValue()}));
        return found.stream()
                .map(d -> {
                    long[] counts = booked.getOrDefault(d.getId(), new long[2]);
                    int remaining = PrioritySeats.remaining(d.getCapacity(), counts[0], counts[1],
                            PrioritySeats.held(d, now, config), priorityUser);
                    return new Candidate(d, remaining, (int) counts[0], List.of());
                })
                .toList();
    }

    private DepartureResponse toResponse(Candidate c) {
        Departure d = c.departure();
        return new DepartureResponse(d.getId(), d.getElevator().getCode(),
                d.getElevator().getCore().getBuilding().getCode(), d.getDepartureAt(), d.getCapacity(),
                c.remaining(), c.booked(), d.getDepartureAt().minus(config.bookingClosesBefore()), d.getStatus());
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
