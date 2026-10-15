package com.smartelevate.turn.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.smartelevate.user.model.Role;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Consumer;
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

/** Congestión y KPIs de punta a punta (H2 + migraciones): seguridad, exclusión de simulados y período anterior. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(AdminKpiApiTest.FixedClockConfig.class)
class AdminKpiApiTest {

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

    private User admin;
    private User common;

    @AfterEach
    void clean() {
        checkIns.deleteAll();
        kiosks.deleteAll();
        reservations.deleteAll();
        departures.deleteAll();
        users.deleteAll();
    }

    private User newUser(String email, String legajo, Role role) {
        User u = new User("Usuario " + legajo, email, legajo, "x", DeclaredUserType.STUDENT, NOW);
        u.verifyEmail();
        u.assignRole(role);
        return users.saveAndFlush(u);
    }

    @BeforeEach
    void seed() {
        clean();
        admin = newUser("admin@uade.edu.ar", "1000001", Role.ADMIN);
        common = newUser("comun@uade.edu.ar", "1000002", Role.USER);
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        String jwt = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(user.getId().toString()).issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(900)).build())).getTokenValue();
        return request.header("Authorization", "Bearer " + jwt);
    }

    private Reservation reservation(Departure departure, int seat, Consumer<Reservation> change) {
        Reservation r = new Reservation(common.getId(), departure, seat, 0, 8,
                departure.getDepartureAt().minusSeconds(600));
        change.accept(r);
        return reservations.save(r);
    }

    private Departure departure(Duration fromNow) {
        return departures.save(new Departure(elevators.findById("33").orElseThrow(), NOW.plus(fromNow), 10));
    }

    @Test
    void kpisAndShiftsAreOnlyForAdmins() throws Exception {
        mockMvc.perform(get("/api/admin/kpis").param("period", "TODAY")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/shifts")).andExpect(status().isUnauthorized());
        mockMvc.perform(as(common, get("/api/admin/kpis")).param("period", "TODAY"))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(common, get("/api/admin/shifts"))).andExpect(status().isForbidden());
    }

    @Test
    void congestionNeedsASessionButNoRole() throws Exception {
        mockMvc.perform(get("/api/congestion")).andExpect(status().isUnauthorized());
        Departure next = departure(Duration.ofMinutes(10));
        reservation(next, 1, r -> { });
        reservation(next, 2, r -> { });

        mockMvc.perform(as(common, get("/api/congestion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshAfterSeconds").value(120))
                .andExpect(jsonPath("$.cores", hasSize(5)))
                .andExpect(jsonPath("$.cores[?(@.coreId == 'IND2')].occupancyPercent").value(20.0))
                .andExpect(jsonPath("$.cores[?(@.coreId == 'IND2')].level").value("LOW"))
                .andExpect(jsonPath("$.cores[?(@.coreId == 'L1')].occupancyPercent").value(0.0));
    }

    @Test
    void kpisLeaveOutSimulatedCheckInsAndCompareWithYesterday() throws Exception {
        Kiosk simulator = kiosks.save(new Kiosk("33", UUID.randomUUID().toString().replace("-", ""), true, NOW));
        Departure past = departure(Duration.ofMinutes(-10));
        reservation(past, 1, Reservation::markUsed);
        reservation(past, 2, Reservation::markNoShow);
        Reservation simulated = reservation(past, 3, Reservation::markUsed);
        checkIns.save(new CheckIn(simulated.getId(), CheckInOutcome.ON_TIME, "33", CheckInSource.SIMULATED,
                simulator.getId(), past.getDepartureAt()));
        Reservation cancelled = reservation(past, 4, r -> { });
        cancelled.cancel(NOW.minus(Duration.ofHours(1)), Duration.ofMinutes(10));
        reservations.save(cancelled);
        reservation(departure(Duration.ofMinutes(10)), 1, r -> { });
        reservation(departure(Duration.ofDays(-1)), 1, Reservation::markUsed);

        mockMvc.perform(as(admin, get("/api/admin/kpis")).param("period", "TODAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-10-13T03:00:00Z"))
                .andExpect(jsonPath("$.to").value("2026-10-14T03:00:00Z"))
                .andExpect(jsonPath("$.reservations").value(3))
                .andExpect(jsonPath("$.checkIns").value(1))
                .andExpect(jsonPath("$.checkInPercent").value(50.0))
                .andExpect(jsonPath("$.avgOccupancy").value(1.0))
                .andExpect(jsonPath("$.avgWaitSeconds").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.wait5To10Percent").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.cores", hasSize(1)))
                .andExpect(jsonPath("$.cores[0].coreId").value("IND2"))
                .andExpect(jsonPath("$.cores[0].occupancyPercent").value(10.0))
                .andExpect(jsonPath("$.previous.from").value("2026-10-12T03:00:00Z"))
                .andExpect(jsonPath("$.previous.reservations").value(1))
                .andExpect(jsonPath("$.previous.checkInPercent").value(100.0));

        mockMvc.perform(as(admin, get("/api/admin/kpis")).param("period", "TODAY").param("buildingId", "LIMA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations").value(0))
                .andExpect(jsonPath("$.previous").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void invalidFiltersAreBadRequests() throws Exception {
        mockMvc.perform(as(admin, get("/api/admin/kpis"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("period"));
        mockMvc.perform(as(admin, get("/api/admin/kpis")).param("period", "YEAR"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(admin, get("/api/admin/kpis")).param("period", "TODAY").param("buildingId", "CABA"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(admin, get("/api/admin/kpis")).param("period", "TODAY").param("shiftId", "NIGHT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shiftsListTheMorningShift() throws Exception {
        mockMvc.perform(as(admin, get("/api/admin/shifts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("MORNING"))
                .andExpect(jsonPath("$[0].name").value("Turno mañana"))
                .andExpect(jsonPath("$[0].startsAt").value("07:00"))
                .andExpect(jsonPath("$[0].endsAt").value("12:15"));
    }
}
