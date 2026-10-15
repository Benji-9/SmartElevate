package com.smartelevate.turn.service;

import com.smartelevate.common.config.ConfigService;
import com.smartelevate.common.time.CampusTime;
import com.smartelevate.elevator.repository.CoreRepository;
import com.smartelevate.turn.dto.CongestionLevel;
import com.smartelevate.turn.dto.CongestionResponse;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.repository.DepartureRepository;
import com.smartelevate.turn.repository.ReservationRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Congestión ahora (#220): por núcleo, lugares reservados / capacidad de las salidas programadas de la ventana de
 * reserva (de ahora a {@code booking_opens_before}). Solo conteos: ningún dato personal.
 * <p>
 * Usa {@code countActiveByDeparture} directo y no {@link KpiEvents}, a propósito: mide lugares ocupados, y una
 * reserva con check-in simulado sigue ocupando el suyo.
 */
// ponytail: las salidas se generan al consultarlas (DepartureService), así que un núcleo sin salidas generadas
// cuenta como 0 %. Si hiciera falta la capacidad "teórica", calcularla con el horario y slot_duration.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CongestionService {

    private final DepartureRepository departures;
    private final ReservationRepository reservations;
    private final CoreRepository cores;
    private final ConfigService config;
    private final CampusTime time;

    public CongestionResponse now() {
        Instant now = time.now();
        List<Departure> found = departures.findScheduledBetween(now, now.plus(config.bookingOpensBefore()));
        Map<Long, Long> booked = found.isEmpty() ? Map.of() : reservations
                .countActiveByDeparture(found.stream().map(Departure::getId).toList()).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).longValue()));
        Map<String, long[]> byCore = new HashMap<>(); // código de núcleo → [reservados, capacidad]
        for (Departure d : found) {
            long[] seats = byCore.computeIfAbsent(d.getElevator().getCore().getCode(), k -> new long[2]);
            seats[0] += booked.getOrDefault(d.getId(), 0L);
            seats[1] += d.getCapacity();
        }
        List<CongestionResponse.Core> result = cores.findAll(Sort.by("name")).stream()
                .map(core -> {
                    long[] seats = byCore.getOrDefault(core.getCode(), new long[2]);
                    double exact = seats[1] == 0 ? 0 : 100.0 * seats[0] / seats[1];
                    // El nivel sale del valor sin redondear: 84,96 % no es HIGH aunque se muestre como 85.
                    return new CongestionResponse.Core(core.getCode(), core.getName(), CongestionLevel.of(exact,
                            config.congestionMediumFrom(), config.congestionHighFrom()),
                            AdminKpiService.round1(exact));
                })
                .toList();
        return new CongestionResponse(now, config.slotDuration().toSeconds(), result);
    }
}
