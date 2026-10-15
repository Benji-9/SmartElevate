package com.smartelevate.turn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartelevate.common.config.ConfigService;
import com.smartelevate.common.time.CampusTime;
import com.smartelevate.elevator.model.Building;
import com.smartelevate.elevator.model.Core;
import com.smartelevate.elevator.model.Elevator;
import com.smartelevate.elevator.model.ElevatorUsage;
import com.smartelevate.elevator.repository.BuildingRepository;
import com.smartelevate.turn.dto.AdminKpisResponse;
import com.smartelevate.turn.dto.AdminKpisResponse.HourReservations;
import com.smartelevate.turn.dto.AdminPeriod;
import com.smartelevate.turn.dto.CongestionLevel;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.Reservation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class AdminKpiServiceTest {

    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Building LIMA = new Building("LIMA", "Lima", -4, 10);
    private static final Building IND = new Building("INDEPENDENCIA", "Independencia", -4, 11);
    private static final Elevator L1 = elevator("01", new Core("L1", LIMA, "Lima 1"));
    private static final Elevator IND1 = elevator("30", new Core("IND1", IND, "Independencia 1"));

    private final KpiEvents events = mock(KpiEvents.class);
    private final BuildingRepository buildings = mock(BuildingRepository.class);
    private final ConfigService config = mock(ConfigService.class);
    private long ids;

    private static Elevator elevator(String code, Core core) {
        return new Elevator(code, core, ElevatorUsage.COMMON, true, Set.of(0, 5));
    }

    private AdminKpiService service(Instant now) {
        when(config.operatingHoursStart()).thenReturn(LocalTime.of(7, 0));
        when(config.operatingHoursEnd()).thenReturn(LocalTime.of(22, 30));
        when(config.morningShiftStart()).thenReturn(LocalTime.of(7, 0));
        when(config.morningShiftEnd()).thenReturn(LocalTime.of(12, 15));
        when(config.noShowAfter()).thenReturn(Duration.ofSeconds(60));
        when(config.turnCapacity()).thenReturn(10);
        when(config.congestionMediumFrom()).thenReturn(60);
        when(config.congestionHighFrom()).thenReturn(85);
        when(buildings.existsById("LIMA")).thenReturn(true);
        when(events.reservations(any(), any())).thenReturn(List.of());
        return new AdminKpiService(events, buildings, config, new CampusTime(Clock.fixed(now, ZoneOffset.UTC), BA));
    }

    private Departure departure(Elevator elevator, Instant at) {
        Departure d = new Departure(elevator, at, 10);
        ReflectionTestUtils.setField(d, "id", ++ids);
        return d;
    }

    private static List<Reservation> reservations(Departure d, int used, int noShows, int active) {
        List<Reservation> list = new ArrayList<>();
        for (int i = 0; i < used + noShows + active; i++) {
            Reservation r = new Reservation(UUID.randomUUID(), d, i + 1, 0, 5, d.getDepartureAt().minusSeconds(600));
            if (i < used) {
                r.markUsed();
            } else if (i < used + noShows) {
                r.markNoShow();
            }
            list.add(r);
        }
        return list;
    }

    @Test
    void todayIsTheBuenosAiresDayAndPreviousIsYesterdayAcrossAMonthChange() {
        // 1/10 a las 00:30 de Buenos Aires (03:30 UTC): "hoy" es el 1/10 y "ayer", el 30/9.
        Instant now = Instant.parse("2026-10-01T03:30:00Z");
        AdminKpiService service = service(now);
        Instant from = Instant.parse("2026-10-01T03:00:00Z");
        Instant previousFrom = Instant.parse("2026-09-30T03:00:00Z");
        when(events.reservations(previousFrom, from))
                .thenReturn(reservations(departure(L1, Instant.parse("2026-09-30T12:00:00Z")), 1, 1, 0));

        AdminKpisResponse kpis = service.kpis(AdminPeriod.TODAY, null, null);

        verify(events).reservations(from, Instant.parse("2026-10-02T03:00:00Z"));
        assertThat(kpis.from()).isEqualTo(from);
        assertThat(kpis.to()).isEqualTo(Instant.parse("2026-10-02T03:00:00Z"));
        assertThat(kpis.previous()).isNotNull();
        assertThat(kpis.previous().from()).isEqualTo(previousFrom);
        assertThat(kpis.previous().to()).isEqualTo(from);
        assertThat(kpis.previous().reservations()).isEqualTo(2);
        assertThat(kpis.previous().checkInPercent()).isEqualTo(50.0);
        assertThat(kpis.previous().avgOccupancy()).isEqualTo(1.0);
        assertThat(kpis.previous().avgWaitSeconds()).isNull();
        assertThat(kpis.previous().wait5To10Percent()).isNull();
    }

    @Test
    void weekAndMonthCoverTheirDaysAndPreviousIsTheSameLengthBefore() {
        AdminKpiService service = service(Instant.parse("2026-10-03T15:00:00Z"));

        AdminKpisResponse week = service.kpis(AdminPeriod.WEEK, null, null);
        assertThat(week.from()).isEqualTo(Instant.parse("2026-09-27T03:00:00Z"));
        assertThat(week.to()).isEqualTo(Instant.parse("2026-10-04T03:00:00Z"));
        verify(events).reservations(Instant.parse("2026-09-20T03:00:00Z"), Instant.parse("2026-09-27T03:00:00Z"));

        AdminKpisResponse month = service.kpis(AdminPeriod.MONTH, null, null);
        assertThat(month.from()).isEqualTo(Instant.parse("2026-09-04T03:00:00Z"));
        verify(events).reservations(Instant.parse("2026-08-05T03:00:00Z"), Instant.parse("2026-09-04T03:00:00Z"));
    }

    @Test
    void withoutReservationsPreviousIsNullAndRatesAreNotAvailable() {
        AdminKpisResponse kpis = service(Instant.parse("2026-10-13T15:00:00Z")).kpis(AdminPeriod.TODAY, null, null);

        assertThat(kpis.previous()).isNull();
        assertThat(kpis.reservations()).isZero();
        assertThat(kpis.checkInPercent()).isNull();
        assertThat(kpis.avgOccupancy()).isNull();
        assertThat(kpis.avgWaitSeconds()).isNull();
        assertThat(kpis.wait5To10Percent()).isNull();
        assertThat(kpis.baselinePercent()).isEqualTo(43.5);
        assertThat(kpis.capacity()).isEqualTo(10);
        assertThat(kpis.cores()).isEmpty();
        assertThat(kpis.reservationsByHour()).extracting(HourReservations::hour).containsExactly(
                7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22);
    }

    @Test
    void checkInsAndOccupancyCountOnlyResolvedDepartures() {
        Instant now = Instant.parse("2026-10-13T15:00:00Z"); // 12:00 en Buenos Aires
        AdminKpiService service = service(now);
        List<Reservation> today = new ArrayList<>();
        today.addAll(reservations(departure(L1, now.minusSeconds(61)), 9, 1, 0));   // resuelta: 9 / 10
        today.addAll(reservations(departure(L1, now.minusSeconds(60)), 0, 0, 3));   // todavía no resuelta
        today.addAll(reservations(departure(IND1, now.minusSeconds(4 * 3600)), 3, 2, 0)); // 8:00, resuelta: 3 / 10
        when(events.reservations(Instant.parse("2026-10-13T03:00:00Z"), Instant.parse("2026-10-14T03:00:00Z")))
                .thenReturn(today);

        AdminKpisResponse kpis = service.kpis(AdminPeriod.TODAY, null, null);

        assertThat(kpis.reservations()).isEqualTo(18);
        assertThat(kpis.checkIns()).isEqualTo(12);
        assertThat(kpis.checkInPercent()).isEqualTo(80.0); // 12 / 15 resueltas
        assertThat(kpis.avgOccupancy()).isEqualTo(6.0);   // (9 + 3) / 2 salidas resueltas
        assertThat(kpis.cores()).extracting(AdminKpisResponse.CoreKpis::coreId).containsExactly("IND1", "L1");
        assertThat(kpis.cores().get(0).occupancyPercent()).isEqualTo(30.0);
        assertThat(kpis.cores().get(0).congestion()).isEqualTo(CongestionLevel.LOW);
        assertThat(kpis.cores().get(1).reservations()).isEqualTo(13);
        assertThat(kpis.cores().get(1).occupancyPercent()).isEqualTo(90.0);
        assertThat(kpis.cores().get(1).congestion()).isEqualTo(CongestionLevel.HIGH);
        assertThat(kpis.cores().get(1).avgWaitSeconds()).isNull();
        assertThat(kpis.reservationsByHour()).filteredOn(h -> h.reservations() > 0)
                .containsExactly(new HourReservations(8, 5), new HourReservations(11, 13));
    }

    @Test
    void aCoreWithOnlyUpcomingDeparturesHasNoOccupancyNorLevel() {
        Instant now = Instant.parse("2026-10-13T15:00:00Z");
        AdminKpiService service = service(now);
        when(events.reservations(Instant.parse("2026-10-13T03:00:00Z"), Instant.parse("2026-10-14T03:00:00Z")))
                .thenReturn(reservations(departure(L1, now.plusSeconds(600)), 0, 0, 4));

        AdminKpisResponse kpis = service.kpis(AdminPeriod.TODAY, null, null);

        assertThat(kpis.reservations()).isEqualTo(4);
        assertThat(kpis.checkInPercent()).isNull();
        assertThat(kpis.cores()).singleElement().satisfies(c -> {
            assertThat(c.occupancyPercent()).isNull();
            assertThat(c.congestion()).isNull();
        });
    }

    @Test
    void buildingAndShiftFilterBothPeriods() {
        Instant now = Instant.parse("2026-10-13T20:00:00Z");
        AdminKpiService service = service(now);
        Instant from = Instant.parse("2026-10-13T03:00:00Z");
        List<Reservation> today = new ArrayList<>();
        today.addAll(reservations(departure(L1, Instant.parse("2026-10-13T10:00:00Z")), 1, 0, 0)); // 7:00 entra
        today.addAll(reservations(departure(L1, Instant.parse("2026-10-13T15:14:00Z")), 1, 0, 0)); // 12:14 entra
        today.addAll(reservations(departure(L1, Instant.parse("2026-10-13T15:15:00Z")), 1, 0, 0)); // 12:15 no
        today.addAll(reservations(departure(IND1, Instant.parse("2026-10-13T11:00:00Z")), 1, 0, 0)); // otra sede
        when(events.reservations(from, Instant.parse("2026-10-14T03:00:00Z"))).thenReturn(today);
        when(events.reservations(Instant.parse("2026-10-12T03:00:00Z"), from))
                .thenReturn(reservations(departure(L1, Instant.parse("2026-10-12T18:00:00Z")), 1, 0, 0)); // 15:00

        AdminKpisResponse kpis = service.kpis(AdminPeriod.TODAY, "LIMA", AdminKpiService.MORNING_SHIFT_ID);

        assertThat(kpis.reservations()).isEqualTo(2);
        assertThat(kpis.reservationsByHour()).containsExactly(new HourReservations(7, 1), new HourReservations(8, 0),
                new HourReservations(9, 0), new HourReservations(10, 0), new HourReservations(11, 0),
                new HourReservations(12, 1));
        assertThat(kpis.previous()).as("la de ayer fue fuera del turno mañana").isNull();
    }

    @Test
    void unknownBuildingOrShiftIsABadRequest() {
        AdminKpiService service = service(Instant.parse("2026-10-13T15:00:00Z"));

        assertThatThrownBy(() -> service.kpis(AdminPeriod.TODAY, "CABA", null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("La sede no existe");
        assertThatThrownBy(() -> service.kpis(AdminPeriod.TODAY, null, "NIGHT"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("turno de cursada");
    }

    @Test
    void shiftsComeFromConfig() {
        assertThat(service(Instant.parse("2026-10-13T15:00:00Z")).shifts()).singleElement().satisfies(s -> {
            assertThat(s.id()).isEqualTo("MORNING");
            assertThat(s.name()).isEqualTo("Turno mañana");
            assertThat(s.startsAt()).isEqualTo("07:00");
            assertThat(s.endsAt()).isEqualTo("12:15");
        });
    }

    @ParameterizedTest
    @CsvSource({"0, LOW", "59.9, LOW", "60, MEDIUM", "84.9, MEDIUM", "85, HIGH", "100, HIGH"})
    void levelThresholdsAreInclusive(double percent, CongestionLevel level) {
        assertThat(CongestionLevel.of(percent, 60, 85)).isEqualTo(level);
    }
}
