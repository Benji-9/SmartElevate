package com.smartelevate.turn.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartelevate.common.config.SchedulingConfig;
import com.smartelevate.elevator.model.Kiosk;
import com.smartelevate.elevator.repository.ElevatorRepository;
import com.smartelevate.elevator.repository.KioskRepository;
import com.smartelevate.turn.model.CheckIn;
import com.smartelevate.turn.model.CheckInOutcome;
import com.smartelevate.turn.model.CheckInSource;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.turn.model.ReservationStatus;
import com.smartelevate.turn.repository.CheckInRepository;
import com.smartelevate.turn.repository.DepartureRepository;
import com.smartelevate.turn.repository.NoShowSuspensionRepository;
import com.smartelevate.turn.repository.ReservationRepository;
import com.smartelevate.turn.service.KpiEvents;
import com.smartelevate.turn.service.NoShowService;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Job de no-shows, faltas, suspensión y KPIs sin simulados, contra H2 con las migraciones (#231). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(NoShowApiTest.FixedClockConfig.class)
class NoShowApiTest {

    // Martes 2026-10-13 12:55 en Buenos Aires.
    private static final Instant NOW = Instant.parse("2026-10-13T15:55:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private NoShowService noShows;
    @Autowired
    private KpiEvents kpis;
    @Autowired
    private ApplicationContext context;
    @Autowired
    private UserRepository users;
    @Autowired
    private KioskRepository kiosks;
    @Autowired
    private ElevatorRepository elevators;
    @Autowired
    private DepartureRepository departures;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private CheckInRepository checkIns;
    @Autowired
    private NoShowSuspensionRepository suspensions;

    private User user;

    @AfterEach
    void clean() {
        suspensions.deleteAll();
        checkIns.deleteAll();
        kiosks.deleteAll();
        reservations.deleteAll();
        departures.deleteAll();
        users.deleteAll();
    }

    @BeforeEach
    void seed() {
        clean();
        User u = new User("Ana", "ana@uade.edu.ar", "1001", "x", DeclaredUserType.STUDENT, NOW);
        u.verifyEmail();
        user = users.saveAndFlush(u);
    }

    private Departure departure(Duration fromNow) {
        return departures.save(new Departure(elevators.findById("33").orElseThrow(), NOW.plus(fromNow), 10));
    }

    private Reservation reservation(Duration departureFromNow) {
        return reservations.save(new Reservation(user.getId(), departure(departureFromNow), 1, 0, 8,
                NOW.plus(departureFromNow).minusSeconds(600)));
    }

    private Reservation noShow(Duration departureFromNow) {
        Reservation r = reservation(departureFromNow);
        r.markNoShow();
        return reservations.save(r);
    }

    private ReservationStatus statusOf(Reservation r) {
        return reservations.findById(r.getId()).orElseThrow().getStatus();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        String jwt = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(user.getId().toString()).issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(900)).build())).getTokenValue();
        return request.header("Authorization", "Bearer " + jwt);
    }

    @Test
    void schedulingIsOffInTests() {
        assertThat(context.getBeanNamesForType(SchedulingConfig.class)).isEmpty();
    }

    @Test
    void theJobMarksOnlyAfterSixtySecondsAndDoesNotOverwriteUsedOrCancelled() {
        Reservation atSixty = reservation(Duration.ofSeconds(-60));
        Reservation afterSixty = reservation(Duration.ofSeconds(-61));
        Reservation used = reservation(Duration.ofMinutes(-5));
        used.markUsed();
        reservations.save(used);
        Reservation cancelled = reservation(Duration.ofMinutes(-7));
        cancelled.cancel(NOW.minus(Duration.ofMinutes(30)), Duration.ofMinutes(10));
        reservations.save(cancelled);

        assertThat(noShows.markNoShows()).isEqualTo(1);

        assertThat(statusOf(atSixty)).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(statusOf(afterSixty)).isEqualTo(ReservationStatus.NO_SHOW);
        assertThat(reservations.findById(afterSixty.getId()).orElseThrow().getSeatNumber()).isEqualTo(1);
        assertThat(statusOf(used)).isEqualTo(ReservationStatus.USED);
        assertThat(statusOf(cancelled)).isEqualTo(ReservationStatus.CANCELLED);
        // Idempotente: una segunda pasada no cambia nada.
        assertThat(noShows.markNoShows()).isZero();
        assertThat(statusOf(afterSixty)).isEqualTo(ReservationStatus.NO_SHOW);
    }

    @Test
    void aLateCancellationCountsAsAFaultAndThreeFaultsBlockReserving() throws Exception {
        noShow(Duration.ofDays(-3));
        noShow(Duration.ofDays(-1));
        Reservation late = reservation(Duration.ofMinutes(-5));
        late.cancel(NOW.minus(Duration.ofMinutes(8)), Duration.ofMinutes(10));
        reservations.save(late);
        assertThat(statusOf(late)).isEqualTo(ReservationStatus.CANCELLED_LATE);
        Instant until = NOW.minus(Duration.ofMinutes(5)).plus(Duration.ofHours(24));

        mockMvc.perform(authed(get("/api/me/no-shows")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentNoShows").value(3))
                .andExpect(jsonPath("$.threshold").value(3))
                .andExpect(jsonPath("$.windowDays").value(7))
                .andExpect(jsonPath("$.suspensionHours").value(24))
                .andExpect(jsonPath("$.suspendedUntil").value(until.toString()))
                .andExpect(jsonPath("$.exempt").value(false));

        Departure next = departure(Duration.ofMinutes(10));
        for (int attempt = 0; attempt < 2; attempt++) {
            reserve(next).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_SHOW_SUSPENDED"));
        }
        // La suspensión se guarda una sola vez (V17), desde la falta que completó las 3.
        assertThat(suspensions.findAll()).singleElement().satisfies(s -> {
            assertThat(s.getTriggerFaultAt()).isEqualTo(NOW.minus(Duration.ofMinutes(5)));
            assertThat(s.getEndsAt()).isEqualTo(until);
        });
    }

    private ResultActions reserve(Departure departure) throws Exception {
        return mockMvc.perform(authed(post("/api/reservations")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"departureId\":%d,\"originFloor\":0,\"destinationFloor\":8}".formatted(departure.getId())));
    }

    @Test
    void aSimulatedLateCheckInIsNotAFaultAndDoesNotSuspend() throws Exception {
        Kiosk simulator = kiosks.save(new Kiosk("33", UUID.randomUUID().toString().replace("-", ""), true, NOW));
        noShow(Duration.ofDays(-2));
        noShow(Duration.ofDays(-1));
        Reservation simulatedLate = noShow(Duration.ofMinutes(-5));
        checkIns.save(new CheckIn(simulatedLate.getId(), CheckInOutcome.LATE, "33", CheckInSource.SIMULATED,
                simulator.getId(), NOW.minus(Duration.ofMinutes(3))));

        mockMvc.perform(authed(get("/api/me/no-shows")))
                .andExpect(jsonPath("$.recentNoShows").value(2))
                .andExpect(jsonPath("$.suspendedUntil").doesNotExist());
        reserve(departure(Duration.ofMinutes(10))).andExpect(status().isCreated());
        assertThat(suspensions.count()).isZero();
    }

    @Test
    void withoutFaultsTheStatusIsEmpty() throws Exception {
        mockMvc.perform(authed(get("/api/me/no-shows")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentNoShows").value(0))
                .andExpect(jsonPath("$.suspendedUntil").doesNotExist())
                .andExpect(jsonPath("$.exempt").value(false));
        mockMvc.perform(get("/api/me/no-shows")).andExpect(status().isUnauthorized());
    }

    @Test
    void simulatedCheckInsAreLeftOutOfTheKpis() {
        Kiosk real = kiosks.save(new Kiosk("33", UUID.randomUUID().toString().replace("-", ""), false, NOW));
        Kiosk simulator = kiosks.save(new Kiosk("33", UUID.randomUUID().toString().replace("-", ""), true, NOW));
        Reservation realLate = noShow(Duration.ofMinutes(-2));
        Reservation simulatedLate = noShow(Duration.ofMinutes(-3));
        Reservation simulatedOnTime = reservation(Duration.ofMinutes(-4));
        Reservation unscanned = noShow(Duration.ofMinutes(-6));
        checkIns.save(new CheckIn(realLate.getId(), CheckInOutcome.LATE, "33", CheckInSource.REAL, real.getId(),
                NOW.minusSeconds(30)));
        checkIns.save(new CheckIn(simulatedLate.getId(), CheckInOutcome.LATE, "33", CheckInSource.SIMULATED,
                simulator.getId(), NOW.minusSeconds(60)));
        checkIns.save(new CheckIn(simulatedOnTime.getId(), CheckInOutcome.ON_TIME, "33", CheckInSource.SIMULATED,
                simulator.getId(), NOW.minusSeconds(240)));
        Instant from = NOW.minus(Duration.ofHours(1));

        // El ON_TIME simulado no cuenta como asistencia real.
        assertThat(kpis.checkIns(from, NOW)).singleElement()
                .satisfies(c -> assertThat(c.getReservationId()).isEqualTo(realLate.getId()));
        assertThat(kpis.noShows(from, NOW)).extracting(Reservation::getId)
                .containsExactlyInAnyOrder(realLate.getId(), unscanned.getId());
    }
}
