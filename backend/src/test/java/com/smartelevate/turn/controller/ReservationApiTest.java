package com.smartelevate.turn.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.smartelevate.elevator.model.Kiosk;
import com.smartelevate.elevator.repository.ElevatorRepository;
import com.smartelevate.elevator.repository.KioskRepository;
import com.smartelevate.turn.model.CheckIn;
import com.smartelevate.turn.model.CheckInOutcome;
import com.smartelevate.turn.model.CheckInSource;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.turn.repository.CheckInRepository;
import com.smartelevate.turn.repository.DepartureRepository;
import com.smartelevate.turn.repository.ReservationRepository;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Reserva activa, detalle e historial paginado de punta a punta, contra H2 con las migraciones (#214). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(ReservationApiTest.FixedClockConfig.class)
class ReservationApiTest {

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

    private User user;
    private User other;

    @AfterEach
    void clean() {
        checkIns.deleteAll();
        kiosks.deleteAll();
        reservations.deleteAll();
        departures.deleteAll();
        users.deleteAll();
    }

    @BeforeEach
    void seed() {
        clean();
        user = newUser("ana@uade.edu.ar", "1001");
        other = newUser("beto@uade.edu.ar", "1002");
    }

    private User newUser(String email, String legajo) {
        User u = new User("Ana", email, legajo, "x", DeclaredUserType.STUDENT, NOW);
        u.verifyEmail();
        return users.saveAndFlush(u);
    }

    private Reservation reservation(User owner, String elevator, Duration departureFromNow) {
        Departure d = departures.save(new Departure(elevators.findById(elevator).orElseThrow(),
                NOW.plus(departureFromNow), 10));
        return reservations.save(new Reservation(owner.getId(), d, 1, 0, 8, NOW.minus(Duration.ofHours(1))));
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, User as) {
        String jwt = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(as.getId().toString()).issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(900)).build())).getTokenValue();
        return request.header("Authorization", "Bearer " + jwt);
    }

    @Test
    void activeIsTheNextActiveOneAndStaysWhileCheckInIsOpen() throws Exception {
        mockMvc.perform(authed(get("/api/reservations/active"), user))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        // Pasada la ventana de check-in (2 min) ya no es "la activa"; una a 90 s de su salida, todavía sí.
        reservation(user, "33", Duration.ofMinutes(-3));
        Reservation current = reservation(user, "33", Duration.ofSeconds(-90));
        reservation(user, "33", Duration.ofMinutes(20));

        mockMvc.perform(authed(get("/api/reservations/active"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(current.getId()))
                .andExpect(jsonPath("$.elevator").value("33"))
                .andExpect(jsonPath("$.coreName").isString())
                .andExpect(jsonPath("$.buildingName").isString())
                .andExpect(jsonPath("$.cancelCountsAsNoShow").value(true))
                .andExpect(jsonPath("$.checkInOutcome").doesNotExist());
        mockMvc.perform(authed(get("/api/reservations/active"), other)).andExpect(status().isNoContent());
    }

    @Test
    void detailIsOnlyForTheOwnerAndBringsTheCheckIn() throws Exception {
        Kiosk simulator = kiosks.save(new Kiosk("33", UUID.randomUUID().toString().replace("-", ""), true, NOW));
        Reservation used = reservation(user, "33", Duration.ofMinutes(-10));
        used.markUsed();
        reservations.save(used);
        checkIns.save(new CheckIn(used.getId(), CheckInOutcome.OTHER_ELEVATOR, "34", CheckInSource.SIMULATED,
                simulator.getId(), NOW.minus(Duration.ofMinutes(10))));

        mockMvc.perform(authed(get("/api/reservations/" + used.getId()), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("USED"))
                .andExpect(jsonPath("$.checkInOutcome").value("OTHER_ELEVATOR"))
                .andExpect(jsonPath("$.cancelCountsAsNoShow").value(false));
        mockMvc.perform(authed(get("/api/reservations/" + used.getId()), other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void historyWalksEveryReservationOnceNewestFirstEvenWithTies() throws Exception {
        List<Long> expected = new ArrayList<>();
        // 23 salidas, más una segunda reserva a la misma hora en otro ascensor (empate en departureAt).
        for (int i = 0; i < 23; i++) {
            Reservation r = reservation(user, "33", Duration.ofMinutes(-5L * i));
            expected.add(r.getId());
            if (i == 19) {
                Reservation tie = reservation(user, "34", Duration.ofMinutes(-5L * i));
                expected.add(expected.size() - 1, tie.getId()); // mismo instante: primero el id mayor
            }
        }
        reservation(other, "33", Duration.ofMinutes(-1));

        List<Long> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            MockHttpServletRequestBuilder request = get("/api/reservations/history");
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            String body = mockMvc.perform(authed(request, user))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            List<Number> ids = JsonPath.read(body, "$.items[*].id");
            ids.forEach(id -> seen.add(id.longValue()));
            cursor = JsonPath.read(body, "$.nextCursor");
            pages++;
        } while (cursor != null);

        assertThat(pages).isEqualTo(2);
        assertThat(seen).containsExactlyElementsOf(expected);
    }

    @Test
    void historyRejectsAnInvalidCursorAndEverythingNeedsAToken() throws Exception {
        mockMvc.perform(authed(get("/api/reservations/history").param("cursor", "basura"), user))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/reservations/active")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reservations/history")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reservations/1")).andExpect(status().isUnauthorized());
    }
}
