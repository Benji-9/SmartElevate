package com.smartelevate.turn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartelevate.common.config.ConfigService;
import com.smartelevate.common.time.CampusTime;
import com.smartelevate.elevator.model.Building;
import com.smartelevate.elevator.model.Core;
import com.smartelevate.elevator.model.Elevator;
import com.smartelevate.elevator.model.ElevatorUsage;
import com.smartelevate.elevator.repository.CoreRepository;
import com.smartelevate.turn.dto.CongestionLevel;
import com.smartelevate.turn.dto.CongestionResponse;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.repository.DepartureRepository;
import com.smartelevate.turn.repository.ReservationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

class CongestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:55:00Z");
    private static final Building LIMA = new Building("LIMA", "Lima", -4, 10);
    private static final Core L1 = new Core("L1", LIMA, "Lima 1");
    private static final Core L2 = new Core("L2", LIMA, "Lima 2");
    private static final Core L3 = new Core("L3", LIMA, "Lima 3");

    private final DepartureRepository departures = mock(DepartureRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final CoreRepository cores = mock(CoreRepository.class);
    private final ConfigService config = mock(ConfigService.class);
    private final CongestionService service = new CongestionService(departures, reservations, cores, config,
            new CampusTime(Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("America/Argentina/Buenos_Aires")));

    private static Departure departure(long id, Core core) {
        Departure d = new Departure(new Elevator("E" + id, core, ElevatorUsage.COMMON, true, Set.of(0)),
                NOW.plusSeconds(120 * id), 10);
        ReflectionTestUtils.setField(d, "id", id);
        return d;
    }

    @Test
    void thresholdsCompareTheUnroundedValue() {
        when(config.bookingOpensBefore()).thenReturn(Duration.ofMinutes(30));
        when(config.slotDuration()).thenReturn(Duration.ofMinutes(2));
        when(config.congestionMediumFrom()).thenReturn(60);
        when(config.congestionHighFrom()).thenReturn(85);
        when(cores.findAll(any(Sort.class))).thenReturn(List.of(L1));
        List<Departure> found = new java.util.ArrayList<>();
        List<Object[]> counts = new java.util.ArrayList<>();
        // 235 salidas = 2350 lugares.
        for (long id = 1; id <= 235; id++) {
            found.add(departure(id, L1));
            counts.add(new Object[] {id, id <= 199 ? 10L : (id == 200 ? 9L : 0L), 0L});
        }
        // Reservados: 199 * 10 + 9 = 1999 de 2350 = 85,06 %.
        when(departures.findScheduledBetween(NOW, NOW.plus(Duration.ofMinutes(30)))).thenReturn(found);
        when(reservations.countActiveByDeparture(found.stream().map(Departure::getId).toList())).thenReturn(counts);
        assertThat(service.now().cores().getFirst().level()).isEqualTo(CongestionLevel.HIGH);

        // 1997 / 2350 = 84,98 %: se muestra como 85,0 pero no llega al umbral, es MEDIUM.
        counts.set(199, new Object[] {200L, 7L, 0L});
        CongestionResponse.Core core = service.now().cores().getFirst();
        assertThat(core.occupancyPercent()).isEqualTo(85.0);
        assertThat(core.level()).isEqualTo(CongestionLevel.MEDIUM);
    }

    @Test
    void levelComesFromReservedSeatsOverCapacityOfTheBookingWindow() {
        when(config.bookingOpensBefore()).thenReturn(Duration.ofMinutes(30));
        when(config.slotDuration()).thenReturn(Duration.ofMinutes(2));
        when(config.congestionMediumFrom()).thenReturn(60);
        when(config.congestionHighFrom()).thenReturn(85);
        when(cores.findAll(any(Sort.class))).thenReturn(List.of(L1, L2, L3));
        when(departures.findScheduledBetween(NOW, NOW.plus(Duration.ofMinutes(30))))
                .thenReturn(List.of(departure(1, L1), departure(2, L1), departure(3, L2)));
        // L1: (9 + 8) / 20 = 85 % → alta, justo en el umbral. L2: 6 / 10 = 60 % → media. L3: sin salidas.
        when(reservations.countActiveByDeparture(List.of(1L, 2L, 3L))).thenReturn(List.of(
                new Object[] {1L, 9L, 0L}, new Object[] {2L, 8L, 1L}, new Object[] {3L, 6L, 0L}));

        CongestionResponse snapshot = service.now();

        assertThat(snapshot.updatedAt()).isEqualTo(NOW);
        assertThat(snapshot.refreshAfterSeconds()).isEqualTo(120);
        assertThat(snapshot.cores()).containsExactly(
                new CongestionResponse.Core("L1", "Lima 1", CongestionLevel.HIGH, 85.0),
                new CongestionResponse.Core("L2", "Lima 2", CongestionLevel.MEDIUM, 60.0),
                new CongestionResponse.Core("L3", "Lima 3", CongestionLevel.LOW, 0.0));
    }
}
