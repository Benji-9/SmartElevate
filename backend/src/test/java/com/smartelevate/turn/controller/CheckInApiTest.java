package com.smartelevate.turn.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartelevate.elevator.model.Kiosk;
import com.smartelevate.elevator.repository.ElevatorRepository;
import com.smartelevate.elevator.repository.KioskRepository;
import com.smartelevate.elevator.service.QrTokenService;
import com.smartelevate.priority.repository.PriorityAuditRepository;
import com.smartelevate.turn.model.CheckInSource;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.turn.model.ReservationStatus;
import com.smartelevate.turn.repository.CheckInRepository;
import com.smartelevate.turn.repository.DepartureRepository;
import com.smartelevate.turn.repository.ReservationRepository;
import com.smartelevate.turn.service.CheckInService;
import com.smartelevate.turn.service.ReservationRejectedException;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

/** Check-in de punta a punta (H2 + migraciones) con tokens reales de kioscos y commits reales. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(CheckInApiTest.MovableClockConfig.class)
class CheckInApiTest {

    // Martes 2026-10-13 12:55 en Buenos Aires.
    private static final Instant NOW = Instant.parse("2026-10-13T15:55:00Z");

    /** Reloj que el test puede mover (token vencido). */
    static class MovableClock extends Clock {
        volatile Instant now = NOW;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class MovableClockConfig {
        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock();
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MovableClock clock;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private QrTokenService qrTokens;
    @Autowired
    private CheckInService service;
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
    private PriorityAuditRepository audit;

    private User user;

    @AfterEach
    void clean() {
        checkIns.deleteAll();
        audit.deleteAll();
        kiosks.deleteAll();
        reservations.deleteAll();
        departures.deleteAll();
        users.deleteAll();
        clock.now = NOW;
    }

    @BeforeEach
    void seed() {
        clean();
        User u = new User("Ana", "ana@uade.edu.ar", "1001", "x", DeclaredUserType.STUDENT, NOW);
        u.verifyEmail();
        user = users.saveAndFlush(u);
    }

    private Reservation reservationIn(String elevator, long secondsFromNow) {
        Departure d = departures.save(new Departure(elevators.findById(elevator).orElseThrow(),
                NOW.plusSeconds(secondsFromNow), 10));
        return reservations.save(new Reservation(user.getId(), d, 1, 0, 8, NOW.minusSeconds(600)));
    }

    private Kiosk kiosk(String elevator, boolean simulated) {
        return kiosks.save(new Kiosk(elevator, UUID.randomUUID().toString().replace("-", ""), simulated, NOW));
    }

    private String token(Kiosk kiosk) {
        return qrTokens.issue(kiosk).token();
    }

    private ResultActions scan(String token) throws Exception {
        String jwt = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(user.getId().toString()).issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(900)).build())).getTokenValue();
        return mockMvc.perform(post("/api/check-ins").header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"));
    }

    private ReservationStatus statusOf(Reservation r) {
        return reservations.findById(r.getId()).orElseThrow().getStatus();
    }

    @Test
    void onTimeScanAtTheReservedElevatorUsesTheReservation() throws Exception {
        Reservation r = reservationIn("33", 30);

        scan(token(kiosk("33", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").value(r.getId()))
                .andExpect(jsonPath("$.outcome").value("ON_TIME"))
                .andExpect(jsonPath("$.reservationStatus").value("USED"))
                .andExpect(jsonPath("$.elevator").value("33"))
                .andExpect(jsonPath("$.reservedElevator").value("33"))
                .andExpect(jsonPath("$.source").value("REAL"));

        assertThat(statusOf(r)).isEqualTo(ReservationStatus.USED);
        assertThat(reservations.findById(r.getId()).orElseThrow().getSeatNumber()).isEqualTo(1);
        assertThat(audit.count()).isZero();
    }

    @Test
    void anotherElevatorIsUsedAndRecordsTheScannedOne() throws Exception {
        Reservation r = reservationIn("33", 0);

        scan(token(kiosk("34", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("OTHER_ELEVATOR"))
                .andExpect(jsonPath("$.elevator").value("34"));

        assertThat(statusOf(r)).isEqualTo(ReservationStatus.USED);
        assertThat(checkIns.findAll()).singleElement().satisfies(c -> assertThat(c.getElevatorCode()).isEqualTo("34"));
    }

    @Test
    void lateScanIsRecordedButCountsAsNoShow() throws Exception {
        Reservation r = reservationIn("33", -90);

        scan(token(kiosk("33", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("LATE"))
                .andExpect(jsonPath("$.reservationStatus").value("NO_SHOW"));

        assertThat(statusOf(r)).isEqualTo(ReservationStatus.NO_SHOW);
        assertThat(checkIns.count()).isEqualTo(1);
    }

    @Test
    void outsideTheWindowAndWithoutReservationAreRejected() throws Exception {
        String token = token(kiosk("33", false));
        scan(token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_ACTIVE_RESERVATION"));

        Reservation r = reservationIn("33", 5 * 60);
        scan(token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OUTSIDE_CHECK_IN_WINDOW"));
        assertThat(statusOf(r)).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(checkIns.count()).isZero();
    }

    @Test
    void secondReadIsRejected() throws Exception {
        String token = token(kiosk("33", false));
        reservationIn("33", 0);

        scan(token).andExpect(status().isCreated());
        scan(token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_CHECKED_IN"));
        assertThat(checkIns.count()).isEqualTo(1);
    }

    @Test
    void twoSimultaneousScansOfTheSameReservationCountOnce() throws Exception {
        Reservation r = reservationIn("33", 0);
        String token = token(kiosk("33", false));
        int attempts = 6;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                Callable<Object> task = () -> {
                    start.await();
                    try {
                        return service.checkIn(user.getId(), token);
                    } catch (ReservationRejectedException e) {
                        return e.getReason();
                    }
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            assertThat(results).filteredOn(o -> o == ReservationRejectedException.Reason.ALREADY_CHECKED_IN)
                    .hasSize(attempts - 1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(checkIns.count()).isEqualTo(1);
        assertThat(statusOf(r)).isEqualTo(ReservationStatus.USED);
    }

    @Test
    void alteredExpiredAndRevokedTokensAreRejectedWithoutSayingWhy() throws Exception {
        Reservation r = reservationIn("33", 0);
        Kiosk kiosk = kiosk("33", false);
        String token = token(kiosk);
        String[] parts = token.split("\\.");
        // Mismo payload con la firma alterada en un carácter; sin firma; basura.
        String tampered = parts[0] + "." + (parts[1].charAt(0) == 'A' ? 'B' : 'A') + parts[1].substring(1);

        for (String bad : new String[] {tampered, "no-es-un-token", parts[0]}) {
            scan(bad).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_QR_TOKEN"))
                    .andExpect(jsonPath("$.message").value("El QR no es válido o ya venció. Escaneá el que está en "
                            + "pantalla."));
        }

        clock.now = NOW.plusSeconds(61); // pasó el ciclo vigente y el de tolerancia (TTL 30 s)
        scan(token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QR_TOKEN"));

        clock.now = NOW;
        kiosk.revoke(NOW);
        kiosks.save(kiosk);
        scan(token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QR_TOKEN"));

        assertThat(statusOf(r)).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(checkIns.count()).isZero();
    }

    @Test
    void simulatedKioskCheckInIsMarkedAndAudited() throws Exception {
        reservationIn("33", 0);
        Kiosk simulator = kiosk("33", true);

        scan(token(simulator)).andExpect(status().isCreated()).andExpect(jsonPath("$.source").value("SIMULATED"));

        assertThat(checkIns.findAll()).singleElement().satisfies(c -> {
            assertThat(c.getSource()).isEqualTo(CheckInSource.SIMULATED);
            assertThat(c.getKioskId()).isEqualTo(simulator.getId());
        });
        assertThat(audit.findAll()).singleElement().satisfies(a -> {
            assertThat(a.getAction()).isEqualTo("CHECK_IN_SIMULATED");
            assertThat(a.getActorId()).isEqualTo(user.getId());
            assertThat(a.getTargetKioskId()).isEqualTo(simulator.getId());
        });
    }

    @Test
    void needsASessionAndAToken() throws Exception {
        mockMvc.perform(post("/api/check-ins").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        scan("").andExpect(status().isBadRequest()).andExpect(jsonPath("$.violations[0].field").value("token"));
    }
}
