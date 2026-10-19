package com.smartelevate.turn.service;

import com.smartelevate.common.config.ConfigService;
import com.smartelevate.common.time.CampusTime;
import com.smartelevate.elevator.model.Core;
import com.smartelevate.elevator.repository.BuildingRepository;
import com.smartelevate.turn.dto.AdminKpisResponse;
import com.smartelevate.turn.dto.AdminKpisResponse.CoreKpis;
import com.smartelevate.turn.dto.AdminKpisResponse.HourReservations;
import com.smartelevate.turn.dto.AdminKpisResponse.PreviousKpis;
import com.smartelevate.turn.dto.AdminPeriod;
import com.smartelevate.turn.dto.AdminShiftResponse;
import com.smartelevate.turn.dto.CongestionLevel;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.turn.model.ReservationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * KPIs del panel admin (kpis.md, #220 y #180). Las reservas salen solo de {@link KpiEvents} (sin las de check-in
 * simulado). Check-ins y ocupación cuentan las salidas <b>resueltas</b>: pasado {@code no_show_after}, una reserva ya
 * es USED o no lo va a ser. La espera real queda en null: necesita la encuesta, fuera del MVP.
 */
// ponytail: carga las reservas del período y agrega en memoria (un mes ≈ miles de filas). Agregar en SQL si crece.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminKpiService {

    /** Línea base de la encuesta inicial (kpis.md): % que esperaba entre 5 y 10 min. Es un dato, no un parámetro. */
    static final double BASELINE_WAIT_5_TO_10_PERCENT = 43.5;
    /** Id del turno mañana (kpis.md#filtros); las horas de cada turno están en app_config. */
    static final String MORNING_SHIFT_ID = "MORNING";
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final KpiEvents events;
    private final BuildingRepository buildings;
    private final ConfigService config;
    private final CampusTime time;

    /** Turno de cursada: hora de Buenos Aires, fin exclusivo. */
    private record Shift(String id, String name, LocalTime start, LocalTime end) {
    }

    private List<Shift> shiftDefinitions() {
        return List.of(
                new Shift(MORNING_SHIFT_ID, "Turno mañana", config.morningShiftStart(), config.morningShiftEnd()),
                new Shift("AFTERNOON", "Turno tarde", config.afternoonShiftStart(), config.afternoonShiftEnd()),
                new Shift("NIGHT", "Turno noche", config.nightShiftStart(), config.nightShiftEnd()));
    }

    public List<AdminShiftResponse> shifts() {
        return shiftDefinitions().stream()
                .map(s -> new AdminShiftResponse(s.id(), s.name(), s.start().format(HH_MM), s.end().format(HH_MM)))
                .toList();
    }

    /**
     * KPIs de los {@code period.days()} días de Buenos Aires que terminan hoy, y {@code previous} con los mismos
     * filtros sobre los días inmediatamente anteriores de igual largo. {@code buildingCode} o {@code shiftId} null =
     * sin filtro.
     */
    public AdminKpisResponse kpis(AdminPeriod period, String buildingCode, String shiftId) {
        if (buildingCode != null && !buildings.existsById(buildingCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sede no existe");
        }
        Shift shift = shiftId == null ? null : shiftDefinitions().stream()
                .filter(s -> s.id().equals(shiftId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El turno de cursada no existe"));
        LocalTime open = config.operatingHoursStart();
        LocalTime close = config.operatingHoursEnd();
        if (shift != null) {
            open = max(open, shift.start());
            close = min(close, shift.end());
        }
        LocalTime start = open;
        LocalTime end = close;
        Predicate<Reservation> filter = r -> {
            Departure d = r.getDeparture();
            LocalTime at = time.toCampus(d.getDepartureAt()).toLocalTime();
            return (buildingCode == null || d.getElevator().getCore().getBuilding().getCode().equals(buildingCode))
                    && (shiftId == null || (!at.isBefore(start) && at.isBefore(end)));
        };

        LocalDate today = time.today();
        LocalDate first = today.minusDays(period.days() - 1L);
        Instant from = midnight(first);
        Instant to = midnight(today.plusDays(1));
        Instant previousFrom = midnight(first.minusDays(period.days()));
        Instant resolvedBefore = time.now().minus(config.noShowAfter());
        List<Reservation> current = events.reservations(from, to).stream().filter(filter).toList();
        List<Reservation> before = events.reservations(previousFrom, from).stream().filter(filter).toList();

        Totals totals = totals(current, resolvedBefore);
        PreviousKpis previous = null;
        if (!before.isEmpty()) {
            Totals p = totals(before, resolvedBefore);
            previous = new PreviousKpis(previousFrom, from, p.reservations(), p.checkInPercent(),
                    p.occupancy().avgPeople(), null, null);
        }
        return new AdminKpisResponse(from, to, null, null, BASELINE_WAIT_5_TO_10_PERCENT, totals.reservations(),
                totals.occupancy().used(), totals.checkInPercent(), totals.occupancy().avgPeople(),
                config.turnCapacity(), byHour(current, start, end), byCore(current, resolvedBefore), previous);
    }

    private List<HourReservations> byHour(List<Reservation> reservations, LocalTime start, LocalTime end) {
        if (!start.isBefore(end)) {
            return List.of();
        }
        Map<Integer, Long> counts = reservations.stream().collect(Collectors.groupingBy(
                r -> time.toCampus(r.getDeparture().getDepartureAt()).getHour(), Collectors.counting()));
        return IntStream.rangeClosed(start.getHour(), end.minusNanos(1).getHour())
                .mapToObj(h -> new HourReservations(h, counts.getOrDefault(h, 0L)))
                .toList();
    }

    private List<CoreKpis> byCore(List<Reservation> reservations, Instant resolvedBefore) {
        Map<String, List<Reservation>> byName = reservations.stream().collect(Collectors.groupingBy(
                r -> core(r).getName(), TreeMap::new, Collectors.toList()));
        return byName.values().stream()
                .map(rs -> {
                    Double percent = totals(rs, resolvedBefore).occupancy().percent();
                    CongestionLevel level = percent == null ? null
                            : CongestionLevel.of(percent, config.congestionMediumFrom(), config.congestionHighFrom());
                    Core core = core(rs.getFirst());
                    return new CoreKpis(core.getCode(), core.getName(), rs.size(), percent, null, level);
                })
                .toList();
    }

    private static Totals totals(List<Reservation> reservations, Instant resolvedBefore) {
        List<Reservation> resolved = reservations.stream()
                .filter(r -> r.getDeparture().getDepartureAt().isBefore(resolvedBefore))
                .toList();
        Map<Long, Integer> capacityByDeparture = new HashMap<>();
        long used = 0;
        for (Reservation r : resolved) {
            capacityByDeparture.putIfAbsent(r.getDeparture().getId(), r.getDeparture().getCapacity());
            if (r.getStatus() == ReservationStatus.USED) {
                used++;
            }
        }
        long seats = capacityByDeparture.values().stream().mapToLong(Integer::longValue).sum();
        Double checkInPercent = resolved.isEmpty() ? null : round1(100.0 * used / resolved.size());
        return new Totals(reservations.size(), checkInPercent,
                new Occupancy(capacityByDeparture.size(), used, seats));
    }

    private record Totals(long reservations, Double checkInPercent, Occupancy occupancy) {
    }

    /** Salidas resueltas con reservas, personas con check-in cumplido y lugares de esas salidas. */
    private record Occupancy(long departures, long used, long seats) {
        Double avgPeople() {
            return departures == 0 ? null : round1((double) used / departures);
        }

        Double percent() {
            return seats == 0 ? null : round1(100.0 * used / seats);
        }
    }

    private Instant midnight(LocalDate date) {
        return time.toInstant(date, LocalTime.MIDNIGHT);
    }

    private static Core core(Reservation r) {
        return r.getDeparture().getElevator().getCore();
    }

    private static LocalTime max(LocalTime a, LocalTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalTime min(LocalTime a, LocalTime b) {
        return a.isBefore(b) ? a : b;
    }

    static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
