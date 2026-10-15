package com.smartelevate.turn.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.smartelevate.elevator.repository.ElevatorRepository;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.turn.model.ReservationStatus;
import com.smartelevate.turn.repository.CheckInRepository;
import com.smartelevate.turn.repository.DepartureRepository;
import com.smartelevate.turn.repository.NoShowSuspensionRepository;
import com.smartelevate.turn.repository.ReservationRepository;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Reglas de cancelación del 14/10 (#218, #179) contra H2 con las migraciones: plazo de V18 y tope diario. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(ReservationCancelApiTest.FixedClockConfig.class)
class ReservationCancelApiTest {

    // Martes 2026-10-13 12:55 en Buenos Aires. Plazo de cancelación sin falta: 10 min (V18).
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
    private UserRepository users;
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

    /** Reserva hecha hoy (hace 1 h) en una salida a {@code fromNow}. */
    private Reservation reservation(Duration fromNow) {
        return reservations.save(new Reservation(user.getId(), departure(fromNow), 1, 0, 8,
                NOW.minus(Duration.ofHours(1))));
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        String jwt = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(user.getId().toString()).issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(900)).build())).getTokenValue();
        return request.header("Authorization", "Bearer " + jwt);
    }

    private ResultActions reserve(Departure departure) throws Exception {
        return mockMvc.perform(authed(post("/api/reservations")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"departureId\":%d,\"originFloor\":0,\"destinationFloor\":8}".formatted(departure.getId())));
    }

    private long reserveAndGetId(Departure departure) throws Exception {
        String body = reserve(departure).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ReservationStatus statusOf(long id) {
        return reservations.findById(id).orElseThrow().getStatus();
    }

    @Test
    void freeCancellationsGiveBackTheDailySlotAndLateOnesDoNot() throws Exception {
        // Hoy: 7 que cuentan (USED, sin faltas para no suspender) y 2 canceladas sin falta, que no cuentan.
        for (int i = 1; i <= 7; i++) {
            Reservation used = reservation(Duration.ofMinutes(-2L * i));
            used.markUsed();
            reservations.save(used);
        }
        for (int i = 8; i <= 9; i++) {
            Reservation free = reservation(Duration.ofMinutes(-2L * i));
            free.cancel(NOW.minus(Duration.ofHours(1)), Duration.ofMinutes(10));
            reservations.save(free);
        }

        // 8.ª del día, a 10 min justos: cancelFreeUntil es ahora y cancelar todavía es sin falta.
        Departure inTen = departure(Duration.ofMinutes(10));
        reserve(inTen).andExpect(status().isCreated()).andExpect(jsonPath("$.cancelFreeUntil").value(NOW.toString()));
        long free = reservations.findAll().stream().filter(r -> r.getStatus() == ReservationStatus.ACTIVE)
                .findFirst().orElseThrow().getId();
        mockMvc.perform(authed(delete("/api/reservations/" + free))).andExpect(status().isNoContent());
        assertThat(statusOf(free)).isEqualTo(ReservationStatus.CANCELLED);

        // Devolvió el lugar: se puede otra. A 5 min (9:59 o menos del plazo), cancelar es tardío.
        long late = reserveAndGetId(departure(Duration.ofMinutes(5)));
        mockMvc.perform(authed(delete("/api/reservations/" + late))).andExpect(status().isNoContent());
        assertThat(statusOf(late)).isEqualTo(ReservationStatus.CANCELLED_LATE);

        // La tardía no devuelve el lugar: 7 + 1 = 8, tope alcanzado.
        reserve(departure(Duration.ofMinutes(12)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_REACHED"));

        mockMvc.perform(authed(get("/api/reservations/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].cancelFreeUntil").value(NOW.minus(Duration.ofMinutes(5)).toString()));
    }

    @Test
    void cannotCancelOnceTheDepartureLeftNorAUsedReservation() throws Exception {
        Reservation left = reservation(Duration.ofSeconds(-30));
        mockMvc.perform(authed(delete("/api/reservations/" + left.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTURE_ALREADY_PASSED"));
        assertThat(statusOf(left.getId())).isEqualTo(ReservationStatus.ACTIVE);

        Reservation used = reservation(Duration.ofSeconds(30));
        used.markUsed();
        reservations.save(used);
        mockMvc.perform(authed(delete("/api/reservations/" + used.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CHECKED_IN"));
        assertThat(statusOf(used.getId())).isEqualTo(ReservationStatus.USED);
    }
}
