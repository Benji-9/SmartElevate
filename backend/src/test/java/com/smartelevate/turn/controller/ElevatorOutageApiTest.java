package com.smartelevate.turn.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartelevate.elevator.repository.ElevatorRepository;
import com.smartelevate.priority.model.PriorityAudit;
import com.smartelevate.priority.repository.PriorityAuditRepository;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.turn.model.ReservationStatus;
import com.smartelevate.turn.repository.CheckInRepository;
import com.smartelevate.turn.repository.DepartureRepository;
import com.smartelevate.turn.repository.ReservationRepository;
import com.smartelevate.turn.service.ElevatorOutageService;
import com.smartelevate.turn.service.TurnMailService;
import com.smartelevate.turn.service.TurnMailService.OutOfServiceNotice;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.Role;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/** Ascensor fuera de servicio (#134) de punta a punta contra H2 con las migraciones. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(ElevatorOutageApiTest.FixedClockConfig.class)
class ElevatorOutageApiTest {

    // Martes 2026-10-13 12:55 en Buenos Aires.
    private static final Instant NOW = Instant.parse("2026-10-13T15:55:00Z");
    private static final String OUT = "/api/admin/elevators/33/out-of-service";
    private static final String IN = "/api/admin/elevators/33/in-service";

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
    private PriorityAuditRepository audit;
    @Autowired
    private ElevatorOutageService outage;
    @Autowired
    private TransactionTemplate tx;
    @MockitoBean
    private TurnMailService mails;

    private User admin;
    private User ana;
    private User bruno;

    @AfterEach
    void clean() {
        audit.deleteAll();
        checkIns.deleteAll();
        reservations.deleteAll();
        departures.deleteAll();
        users.deleteAll();
        elevators.findAll().forEach(e -> {
            e.setActive(true);
            elevators.save(e);
        });
    }

    @BeforeEach
    void seed() {
        clean();
        admin = user("admin@uade.edu.ar", "3000001", Role.ADMIN);
        ana = user("ana@uade.edu.ar", "3000002", Role.USER);
        bruno = user("bruno@uade.edu.ar", "3000003", Role.USER);
    }

    private User user(String email, String legajo, Role role) {
        User u = new User("Usuario " + legajo, email, legajo, "x", DeclaredUserType.STUDENT, NOW);
        u.verifyEmail();
        u.assignRole(role);
        return users.saveAndFlush(u);
    }

    private Departure departure(String elevator, Duration fromNow) {
        return departures.save(new Departure(elevators.findById(elevator).orElseThrow(), NOW.plus(fromNow), 10));
    }

    private Reservation reserve(User u, Departure d) {
        return reservations.save(new Reservation(u.getId(), d, reservations.activeSeats(d.getId()).size() + 1, 0, 8, NOW.minus(Duration.ofHours(1))));
    }

    private MockHttpServletRequestBuilder as(User u, MockHttpServletRequestBuilder request) {
        String jwt = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(u.getId().toString()).issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(900)).build())).getTokenValue();
        return request.header("Authorization", "Bearer " + jwt);
    }

    private ReservationStatus statusOf(Reservation r) {
        return reservations.findById(r.getId()).orElseThrow().getStatus();
    }

    @Test
    void cancelsOnlyActiveFutureReservationsOfThatElevatorWithoutFaultAndFreesTheSeat() throws Exception {
        Reservation future = reserve(ana, departure("33", Duration.ofMinutes(10)));
        Reservation later = reserve(bruno, departure("33", Duration.ofMinutes(30)));
        Reservation used = reserve(ana, departure("33", Duration.ofMinutes(20)));
        used.markUsed();
        reservations.save(used);
        Reservation noShow = reserve(ana, departure("33", Duration.ofMinutes(-5)));
        noShow.markNoShow();
        reservations.save(noShow);
        Reservation passedActive = reserve(bruno, departure("33", Duration.ofMinutes(-1)));
        Reservation otherElevator = reserve(bruno, departure("34", Duration.ofMinutes(10)));

        mockMvc.perform(as(admin, post(OUT)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Averia del motor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("33"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.cancelledReservations").value(2));

        // CANCELLED y no CANCELLED_LATE (aunque la de +10 min esté dentro del plazo tardío) y sin lugar.
        assertThat(statusOf(future)).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(statusOf(later)).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservations.findById(future.getId()).orElseThrow().getSeatNumber()).isNull();
        assertThat(statusOf(used)).isEqualTo(ReservationStatus.USED);
        assertThat(statusOf(noShow)).isEqualTo(ReservationStatus.NO_SHOW);
        assertThat(statusOf(passedActive)).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(statusOf(otherElevator)).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(elevators.findById("33").orElseThrow().isActive()).isFalse();
    }

    @Test
    void givesBackTheDailyQuotaBecauseCancelledDoesNotCount() throws Exception {
        reserve(ana, departure("33", Duration.ofMinutes(10)));
        Instant dayStart = Instant.parse("2026-10-13T03:00:00Z");
        Instant dayEnd = dayStart.plus(Duration.ofDays(1));
        assertThat(countedToday(ana, dayStart, dayEnd)).isEqualTo(1);

        mockMvc.perform(as(admin, post(OUT))).andExpect(status().isOk());

        assertThat(countedToday(ana, dayStart, dayEnd)).isZero();
    }

    private long countedToday(User u, Instant from, Instant to) {
        return reservations.countByUserIdAndStatusNotAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(u.getId(),
                ReservationStatus.CANCELLED, from, to);
    }

    @Test
    void mailsEachAffectedPersonAfterTheCommitWithPlainData() throws Exception {
        Departure d = departure("33", Duration.ofMinutes(10));
        reserve(ana, d);
        reserve(bruno, d);

        mockMvc.perform(as(admin, post(OUT))).andExpect(status().isOk());

        ArgumentCaptor<OutOfServiceNotice> notices = ArgumentCaptor.forClass(OutOfServiceNotice.class);
        verify(mails, times(2)).sendElevatorOutOfService(notices.capture());
        assertThat(notices.getAllValues()).extracting(OutOfServiceNotice::userId)
                .containsExactlyInAnyOrder(ana.getId(), bruno.getId());
        assertThat(notices.getAllValues()).allSatisfy(n -> {
            assertThat(n.elevatorCode()).isEqualTo("33");
            assertThat(n.departureAt()).isEqualTo(d.getDepartureAt());
            assertThat(n.buildingName()).isNotBlank();
        });
    }

    @Test
    void aFailingMailDoesNotStopTheOthersNorUndoTheCancellation() throws Exception {
        Reservation first = reserve(ana, departure("33", Duration.ofMinutes(10)));
        reserve(bruno, departure("33", Duration.ofMinutes(12)));
        when(mails.sendElevatorOutOfService(any())).thenThrow(new IllegalStateException("smtp")).thenReturn(true);

        mockMvc.perform(as(admin, post(OUT))).andExpect(status().isOk());

        verify(mails, times(2)).sendElevatorOutOfService(any());
        assertThat(statusOf(first)).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void aRollbackLeavesEverythingAsItWasAndSendsNoMail() {
        Reservation r = reserve(ana, departure("33", Duration.ofMinutes(10)));

        tx.executeWithoutResult(status -> {
            assertThat(outage.markOutOfService(admin.getId(), "33", null).cancelledReservations()).isEqualTo(1);
            status.setRollbackOnly();
        });

        assertThat(statusOf(r)).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(elevators.findById("33").orElseThrow().isActive()).isTrue();
        assertThat(audit.findAll()).isEmpty();
        verify(mails, never()).sendElevatorOutOfService(any());
    }

    @Test
    void auditsWhoWhichElevatorAndWhenOnlyOnRealChanges() throws Exception {
        mockMvc.perform(as(admin, post(OUT)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Averia del motor\"}")).andExpect(status().isOk());
        mockMvc.perform(as(admin, post(OUT))).andExpect(status().isOk()); // ya estaba: no audita de nuevo
        mockMvc.perform(as(admin, post(IN))).andExpect(status().isOk());
        mockMvc.perform(as(admin, post(IN))).andExpect(status().isOk());

        List<PriorityAudit> rows = audit.findAll().stream()
                .sorted(java.util.Comparator.comparing(PriorityAudit::getCreatedAt)
                        .thenComparing(PriorityAudit::getAction))
                .toList();
        assertThat(rows).hasSize(2).allSatisfy(a -> {
            assertThat(a.getActorId()).isEqualTo(admin.getId());
            assertThat(a.getTargetElevatorCode()).isEqualTo("33");
            assertThat(a.getTargetUserId()).isNull();
            assertThat(a.getCreatedAt()).isEqualTo(NOW);
        });
        assertThat(rows).extracting(PriorityAudit::getAction)
                .containsExactlyInAnyOrder("ELEVATOR_OUT_OF_SERVICE", "ELEVATOR_IN_SERVICE");
        assertThat(rows).filteredOn(a -> a.getAction().equals("ELEVATOR_OUT_OF_SERVICE"))
                .extracting(PriorityAudit::getDetail).containsExactly("Averia del motor");
    }

    @Test
    void anOutOfServiceElevatorIsNotOfferedNorBookableAndComesBackWhenReactivated() throws Exception {
        Departure d = departure("33", Duration.ofMinutes(10));
        mockMvc.perform(as(admin, post(OUT))).andExpect(status().isOk());

        mockMvc.perform(as(ana, get("/api/departures")).param("elevator", "33"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        String body = "{\"departureId\":%d,\"originFloor\":0,\"destinationFloor\":8}".formatted(d.getId());
        mockMvc.perform(as(ana, post("/api/reservations")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("ELEVATOR_NOT_BOOKABLE"));

        mockMvc.perform(as(admin, post(IN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(as(ana, post("/api/reservations")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void adminListShowsStateAndUpcomingReservations() throws Exception {
        reserve(ana, departure("33", Duration.ofMinutes(10)));
        reserve(bruno, departure("33", Duration.ofMinutes(12)));
        reserve(bruno, departure("33", Duration.ofMinutes(-1)));

        mockMvc.perform(as(admin, get("/api/admin/elevators")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='33')].upcomingReservations").value(2))
                .andExpect(jsonPath("$[?(@.code=='33')].active").value(true))
                .andExpect(jsonPath("$[?(@.code=='34')].upcomingReservations").value(0));

        mockMvc.perform(as(admin, post(OUT))).andExpect(status().isOk());

        mockMvc.perform(as(admin, get("/api/admin/elevators")))
                .andExpect(jsonPath("$[?(@.code=='33')].active").value(false))
                .andExpect(jsonPath("$[?(@.code=='33')].upcomingReservations").value(0));
    }

    @Test
    void unknownElevatorIs404() throws Exception {
        mockMvc.perform(as(admin, post("/api/admin/elevators/99/out-of-service")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ELEVATOR_NOT_FOUND"));
        mockMvc.perform(as(admin, post("/api/admin/elevators/99/in-service"))).andExpect(status().isNotFound());
    }

    @Test
    void reasonLongerThan64CharactersIsRejected() throws Exception {
        mockMvc.perform(as(admin, post(OUT)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"%s\"}".formatted("x".repeat(65))))
                .andExpect(status().isBadRequest());
        assertThat(elevators.findById("33").orElseThrow().isActive()).isTrue();
    }

    @Test
    void requiresAnAdmin() throws Exception {
        mockMvc.perform(post(OUT)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(IN)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/elevators")).andExpect(status().isUnauthorized());
        mockMvc.perform(as(ana, post(OUT))).andExpect(status().isForbidden());
        mockMvc.perform(as(ana, post(IN))).andExpect(status().isForbidden());
        mockMvc.perform(as(ana, get("/api/admin/elevators"))).andExpect(status().isForbidden());
        assertThat(elevators.findById("33").orElseThrow().isActive()).isTrue();
        verify(mails, never()).sendElevatorOutOfService(any());
    }
}
