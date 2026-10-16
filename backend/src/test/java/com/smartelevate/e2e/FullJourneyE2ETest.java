package com.smartelevate.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.smartelevate.common.mail.EmailSender;
import com.smartelevate.elevator.repository.ElevatorRepository;
import com.smartelevate.elevator.repository.KioskRepository;
import com.smartelevate.elevator.service.KioskProperties;
import com.smartelevate.priority.repository.PriorityAuditRepository;
import com.smartelevate.priority.repository.UserPriorityRepository;
import com.smartelevate.turn.model.CheckInSource;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.turn.model.ReservationStatus;
import com.smartelevate.turn.repository.CheckInRepository;
import com.smartelevate.turn.repository.DepartureRepository;
import com.smartelevate.turn.repository.NoShowSuspensionRepository;
import com.smartelevate.turn.repository.ReservationRepository;
import com.smartelevate.turn.service.KpiEvents;
import com.smartelevate.turn.service.NoShowService;
import com.smartelevate.user.model.Role;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.RefreshTokenRepository;
import com.smartelevate.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.function.Supplier;
import java.util.regex.Pattern;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Recorrido completo del producto de punta a punta: HTTP real (MockMvc), seguridad, H2 con las migraciones de Flyway
 * y un reloj que el test mueve. Cubre las uniones entre features que los tests por issue no prueban: registro,
 * verificación, sesión, kioscos, reserva, check-in, no-shows y suspensión, cancelaciones, ascensor fuera de servicio,
 * simulador y KPIs. Los mails se capturan en un {@link EmailSender} mockeado. No necesita Docker.
 *
 * <p>Reloj: los usuarios se registran y se loguean con el reloj real (el decoder del JWT valida contra el reloj del
 * sistema) y recién después se fija el reloj en {@link #NOW} para las reglas de turnos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(FullJourneyE2ETest.MovableClockConfig.class)
class FullJourneyE2ETest {

    // Martes 2026-10-13 12:55 en Buenos Aires. Las salidas caen en minutos pares desde las 7:00 (10:00Z).
    private static final Instant NOW = Instant.parse("2026-10-13T15:55:00Z");
    private static final Instant DEP_1600 = Instant.parse("2026-10-13T16:00:00Z");
    private static final Instant DEP_1606 = Instant.parse("2026-10-13T16:06:00Z");
    private static final String PASSWORD = "una-clave-larga";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=(\\S+)");

    /** Reloj que el test mueve. Sin fijar (null) devuelve la hora real. */
    static class MovableClock extends Clock {
        volatile Instant fixed;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            MovableClock self = this;
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return zone;
                }

                @Override
                public Clock withZone(ZoneId other) {
                    return self.withZone(other);
                }

                @Override
                public Instant instant() {
                    return self.instant();
                }
            };
        }

        @Override
        public Instant instant() {
            Instant f = fixed;
            return f != null ? f : Instant.now();
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
    private UserRepository users;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private UserPriorityRepository priorities;
    @Autowired
    private PriorityAuditRepository audit;
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
    @Autowired
    private NoShowService noShows;
    @Autowired
    private KpiEvents kpis;
    @MockitoBean
    private EmailSender emailSender;
    @MockitoSpyBean
    private KioskProperties kioskProps;

    @BeforeEach
    @AfterEach
    void clean() {
        suspensions.deleteAll();
        checkIns.deleteAll();
        audit.deleteAll();
        reservations.deleteAll();
        departures.deleteAll();
        kiosks.deleteAll();
        refreshTokens.deleteAll();
        priorities.deleteAll();
        users.deleteAll();
        elevators.findAll().stream().filter(e -> !e.isActive()).forEach(e -> {
            e.setActive(true);
            elevators.save(e);
        });
        clock.fixed = null;
    }

    // ---------------------------------------------------------------- helpers

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private ResultActions call(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(bearer(request, token));
    }

    private void at(Instant instant) {
        clock.fixed = instant;
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private ResultActions register(String email, String legajo, String type) throws Exception {
        return mockMvc.perform(json(post("/api/auth/register"), """
                {"fullName":"Usuario %s","email":"%s","legajo":"%s","password":"%s","declaredUserType":"%s"}"""
                .formatted(legajo, email, legajo, PASSWORD, type)));
    }

    /** Registra y devuelve el token del link que quedó en el mail de verificación. */
    private String registerAndGetVerificationToken(String email, String legajo, String type) throws Exception {
        clearInvocations(emailSender);
        register(email, legajo, type).andExpect(status().isCreated());
        ArgumentCaptor<String> mail = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq(email), anyString(), mail.capture());
        Matcher m = TOKEN_IN_LINK.matcher(mail.getValue());
        assertThat(m.find()).as("el mail trae el link con el token").isTrue();
        return m.group(1);
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(json(post("/api/auth/login"),
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)));
    }

    private String accessToken(ResultActions login) throws Exception {
        return JsonPath.read(body(login.andExpect(status().isOk())), "$.accessToken");
    }

    /** Registro + verificación por mail + (rol) + login por HTTP. Devuelve el access token. */
    private String signUp(String email, String legajo, String type, Role role) throws Exception {
        String token = registerAndGetVerificationToken(email, legajo, type);
        mockMvc.perform(json(post("/api/auth/verify"), "{\"token\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());
        if (role != Role.USER) {
            User u = users.findByEmail(email).orElseThrow();
            u.assignRole(role);
            users.saveAndFlush(u);
        }
        return accessToken(login(email));
    }

    private String signUp(String email, String legajo) throws Exception {
        return signUp(email, legajo, "STUDENT", Role.USER);
    }

    private record Kiosk(String id, String secret) {
    }

    private Kiosk createKiosk(String adminToken, String elevator, boolean simulated) throws Exception {
        String created = body(call(adminToken, json(post("/api/admin/kiosks"),
                "{\"elevatorId\":\"%s\",\"simulated\":%s}".formatted(elevator, simulated)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.elevatorId").value(elevator))
                .andExpect(jsonPath("$.simulated").value(simulated)));
        return new Kiosk(JsonPath.read(created, "$.id"), JsonPath.read(created, "$.secret"));
    }

    private String qr(Kiosk kiosk) throws Exception {
        return JsonPath.read(body(call(kiosk.secret(), get("/api/kiosks/me/qr-token")).andExpect(status().isOk())),
                "$.token");
    }

    private ResultActions checkIn(String userToken, Kiosk kiosk) throws Exception {
        return call(userToken, json(post("/api/check-ins"), "{\"token\":\"" + qr(kiosk) + "\"}"));
    }

    /** Id de la salida del ascensor a esa hora, tal como la ofrece {@code GET /api/departures} a ese usuario. */
    private long departureId(String userToken, String elevator, Instant departureAt) throws Exception {
        String list = body(call(userToken, get("/api/departures").param("elevator", elevator))
                .andExpect(status().isOk()));
        List<Number> ids = JsonPath.read(list, "$[?(@.departureAt=='" + departureAt + "')].id");
        assertThat(ids).as("salida de %s a las %s", elevator, departureAt).hasSize(1);
        return ids.getFirst().longValue();
    }

    private ResultActions reserve(String userToken, long departureId) throws Exception {
        return call(userToken, json(post("/api/reservations"),
                "{\"departureId\":%d,\"originFloor\":0,\"destinationFloor\":8}".formatted(departureId)));
    }

    private long reserveOk(String userToken, String elevator, Instant departureAt) throws Exception {
        long departure = departureId(userToken, elevator, departureAt);
        return ((Number) JsonPath.read(body(reserve(userToken, departure).andExpect(status().isCreated())), "$.id"))
                .longValue();
    }

    private ReservationStatus statusOf(long reservationId) {
        return reservations.findById(reservationId).orElseThrow().getStatus();
    }

    private long countedToday(String email) {
        Instant dayStart = Instant.parse("2026-10-13T03:00:00Z");
        return reservations.countByUserIdAndStatusNotAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                users.findByEmail(email).orElseThrow().getId(), ReservationStatus.CANCELLED, dayStart,
                dayStart.plus(Duration.ofDays(1)));
    }

    // ---------------------------------------------------------------- 1 y 2: registro, sesión, kiosco, reserva, check-in

    @Test
    void registrationToCheckInHappyPath() throws Exception {
        // 1. Registro: cuenta común sin verificar; el login se rechaza hasta verificar el email.
        String verification = registerAndGetVerificationToken("ana@uade.edu.ar", "1000001", "STUDENT");
        login("ana@uade.edu.ar").andExpect(status().isForbidden());
        mockMvc.perform(json(post("/api/auth/verify"), "{\"token\":\"" + verification + "\"}"))
                .andExpect(status().isNoContent());
        // El token es de un solo uso.
        mockMvc.perform(json(post("/api/auth/verify"), "{\"token\":\"" + verification + "\"}"))
                .andExpect(status().isBadRequest());

        // Login: access token en el body, refresh solo en la cookie HttpOnly.
        ResultActions login = login("ana@uade.edu.ar").andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
        String setCookie = login.andReturn().getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("refresh_token=").contains("HttpOnly", "SameSite=Lax", "Path=/api/auth");
        Cookie firstCookie = login.andReturn().getResponse().getCookie("refresh_token");
        String access = JsonPath.read(body(login), "$.accessToken");

        call(access, get("/api/users/me")).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@uade.edu.ar"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.priority").value("NONE"))
                .andExpect(jsonPath("$.declaredUserType").value("STUDENT"));

        // Refresh: exige el header CSRF, rota la cookie y entrega un access token nuevo.
        mockMvc.perform(post("/api/auth/refresh").cookie(firstCookie)).andExpect(status().isForbidden());
        ResultActions refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(firstCookie)
                .header("X-Requested-With", "SmartElevate")).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
        Cookie secondCookie = refreshed.andReturn().getResponse().getCookie("refresh_token");
        assertThat(secondCookie.getValue()).isNotEqualTo(firstCookie.getValue());
        assertThat(refreshed.andReturn().getResponse().getHeader("Set-Cookie")).contains("HttpOnly", "SameSite=Lax");

        // Logout: expira la cookie y la sesión deja de renovarse.
        String expired = mockMvc.perform(post("/api/auth/logout").cookie(secondCookie)
                        .header("X-Requested-With", "SmartElevate")).andExpect(status().isNoContent())
                .andReturn().getResponse().getHeader("Set-Cookie");
        assertThat(expired).startsWith("refresh_token=;").contains("Max-Age=0");
        mockMvc.perform(post("/api/auth/refresh").cookie(secondCookie).header("X-Requested-With", "SmartElevate"))
                .andExpect(status().isUnauthorized());

        // 2. Un ADMIN (promovido por UPDATE, como el primer admin real) crea el kiosco del ascensor 33.
        String ana = accessToken(login("ana@uade.edu.ar"));
        String admin = signUp("admin@uade.edu.ar", "1000002", "STUDENT", Role.ADMIN);
        call(admin, get("/api/users/me")).andExpect(jsonPath("$.role").value("ADMIN"));
        call(ana, json(post("/api/admin/kiosks"), "{\"elevatorId\":\"33\"}")).andExpect(status().isForbidden());
        Kiosk kiosk = createKiosk(admin, "33", false);
        call(kiosk.secret(), get("/api/kiosks/me/qr-token")).andExpect(status().isOk())
                .andExpect(jsonPath("$.elevatorId").value("33"))
                .andExpect(jsonPath("$.token").isString());

        // Ana ve las salidas y reserva: 10 lugares, 2 retenidos para prioritarios que un común no puede tomar.
        at(NOW);
        String offered = body(call(ana, get("/api/departures").param("elevator", "33")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].elevator").value("33")));
        // Solo la ventana de reserva: de 2 a 30 min antes de la salida.
        List<String> offeredAt = JsonPath.read(offered, "$[*].departureAt");
        assertThat(offeredAt).isNotEmpty().allSatisfy(t -> assertThat(Instant.parse(t))
                .isBetween(NOW.plus(Duration.ofMinutes(2)), NOW.plus(Duration.ofMinutes(30))));
        assertThat((List<Integer>) JsonPath.read(offered, "$[?(@.departureAt=='" + DEP_1600 + "')].remainingSeats"))
                .containsExactly(8);
        long departure = departureId(ana, "33", DEP_1600);

        call(ana, get("/api/reservations/active")).andExpect(status().isNoContent());
        String reserved = body(reserve(ana, departure).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.elevator").value("33"))
                .andExpect(jsonPath("$.seatNumber").value(1))
                .andExpect(jsonPath("$.departureAt").value(DEP_1600.toString()))
                .andExpect(jsonPath("$.cancelFreeUntil").value(DEP_1600.minus(Duration.ofMinutes(10)).toString()))
                .andExpect(jsonPath("$.createdAt").value(NOW.toString())));
        long reservationId = ((Number) JsonPath.read(reserved, "$.id")).longValue();
        assertThat((List<Integer>) JsonPath.read(body(call(ana, get("/api/departures").param("elevator", "33"))),
                "$[?(@.departureAt=='" + DEP_1600 + "')].remainingSeats")).containsExactly(7);
        // No se puede tener dos activas a la vez.
        reserve(ana, departureId(ana, "33", DEP_1606)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_LIMIT_REACHED"));
        call(ana, get("/api/reservations/active")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId));

        // Check-in con el QR del kiosco, 30 s antes de la salida: usada, y el origen es REAL.
        at(DEP_1600.minusSeconds(30));
        checkIn(ana, kiosk).andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").value(reservationId))
                .andExpect(jsonPath("$.outcome").value("ON_TIME"))
                .andExpect(jsonPath("$.reservationStatus").value("USED"))
                .andExpect(jsonPath("$.source").value("REAL"));
        assertThat(statusOf(reservationId)).isEqualTo(ReservationStatus.USED);
        checkIn(ana, kiosk).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CHECKED_IN"));
        call(ana, get("/api/reservations/active")).andExpect(status().isNoContent());
        call(ana, get("/api/reservations/" + reservationId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("USED"))
                .andExpect(jsonPath("$.checkInOutcome").value("ON_TIME"));

        // El job de no-shows no pisa una reserva usada, ni siquiera pasado el umbral.
        at(DEP_1600.plusSeconds(90));
        assertThat(noShows.markNoShows()).isZero();
        assertThat(statusOf(reservationId)).isEqualTo(ReservationStatus.USED);
        call(ana, get("/api/me/no-shows")).andExpect(jsonPath("$.recentNoShows").value(0));
        assertThat(kpis.checkIns(NOW.minus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(1)))).hasSize(1)
                .allSatisfy(c -> assertThat(c.getSource()).isEqualTo(CheckInSource.REAL));
        assertThat(audit.findAll()).extracting("action").containsExactly("KIOSK_CREATED");
    }

    // ---------------------------------------------------------------- 3: no-show, suspensión y prioritarios

    @Test
    void threeNoShowsSuspendACommonUserButNotATeacherApprovedByAnAdmin() throws Exception {
        String admin = signUp("admin@uade.edu.ar", "2000001", "STUDENT", Role.ADMIN);
        String beto = signUp("beto@uade.edu.ar", "2000002");
        String carla = signUp("carla@uade.edu.ar", "2000003", "TEACHER", Role.USER);
        String carlaUuid = users.findByEmail("carla@uade.edu.ar").orElseThrow().getId().toString();

        // Declararse docente no da prioridad: queda en la cola de "Docentes a validar".
        call(carla, get("/api/users/me")).andExpect(jsonPath("$.priority").value("NONE"));
        call(admin, get("/api/admin/teachers/pending")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("carla@uade.edu.ar"));

        // Tres salidas seguidas sin check-in: cada una se marca no-show pasados los 60 s.
        Instant[] departures = {DEP_1600, Instant.parse("2026-10-13T16:04:00Z"), Instant.parse("2026-10-13T16:08:00Z")};
        Instant clockAt = NOW;
        for (int fault = 1; fault <= 3; fault++) {
            at(clockAt);
            long b = reserveOk(beto, "33", departures[fault - 1]);
            long c = reserveOk(carla, "33", departures[fault - 1]);
            // A los 60 s justos todavía no es falta (el check-in hasta +60 s es a tiempo).
            at(departures[fault - 1].plusSeconds(60));
            assertThat(noShows.markNoShows()).isZero();
            at(departures[fault - 1].plusSeconds(61));
            assertThat(noShows.markNoShows()).isEqualTo(2);
            assertThat(statusOf(b)).isEqualTo(ReservationStatus.NO_SHOW);
            assertThat(statusOf(c)).isEqualTo(ReservationStatus.NO_SHOW);
            call(beto, get("/api/me/no-shows")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.recentNoShows").value(fault));
            clockAt = departures[fault - 1].plusSeconds(61);
        }

        // Tercera falta: 24 h sin reservar, contadas desde esa falta (la salida de las 16:08).
        Instant suspendedUntil = departures[2].plus(Duration.ofHours(24));
        long next = departureId(beto, "33", Instant.parse("2026-10-13T16:12:00Z"));
        call(beto, get("/api/me/no-shows")).andExpect(status().isOk())
                .andExpect(jsonPath("$.recentNoShows").value(3))
                .andExpect(jsonPath("$.threshold").value(3))
                .andExpect(jsonPath("$.suspendedUntil").value(suspendedUntil.toString()))
                .andExpect(jsonPath("$.exempt").value(false));
        reserve(beto, next).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_SHOW_SUSPENDED"));
        // Carla todavía es común para el servidor: también queda suspendida.
        reserve(carla, next).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_SHOW_SUSPENDED"));

        // El ADMIN aprueba a la docente: la prioridad se evalúa al reservar, con el mismo token de antes.
        call(admin, post("/api/admin/teachers/" + carlaUuid + "/approve")).andExpect(status().isNoContent());
        call(admin, get("/api/admin/teachers/pending")).andExpect(jsonPath("$.length()").value(0));
        call(carla, get("/api/users/me")).andExpect(jsonPath("$.priority").value("TEACHER"));
        call(carla, get("/api/me/no-shows")).andExpect(status().isOk())
                .andExpect(jsonPath("$.recentNoShows").value(3))
                .andExpect(jsonPath("$.suspendedUntil").doesNotExist())
                .andExpect(jsonPath("$.exempt").value(true));
        reserve(carla, next).andExpect(status().isCreated());
        reserve(beto, next).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_SHOW_SUSPENDED"));
        // Una suspensión por usuario (Carla se guardó cuando todavía era común); aprobarla no crea otra.
        assertThat(suspensions.findAll()).hasSize(2).allSatisfy(s -> assertThat(s.getEndsAt()).isEqualTo(suspendedUntil));

        // Cumplidas las 24 h, el conteo arranca de cero y Beto vuelve a reservar.
        at(suspendedUntil.plus(Duration.ofMinutes(1)));
        call(beto, get("/api/me/no-shows")).andExpect(jsonPath("$.recentNoShows").value(0))
                .andExpect(jsonPath("$.suspendedUntil").doesNotExist());
        reserveOk(beto, "33", Instant.parse("2026-10-14T16:12:00Z"));
    }

    // ---------------------------------------------------------------- 4: cancelaciones

    @Test
    void cancellingWithinTheDeadlineFreesTheQuotaAndCancellingLateCountsAsAFault() throws Exception {
        String dora = signUp("dora@uade.edu.ar", "3000001");
        at(NOW);

        // Sin falta hasta cancelFreeUntil inclusive (salida - 10 min).
        long free = reserveOk(dora, "33", DEP_1606);
        Instant freeUntil = DEP_1606.minus(Duration.ofMinutes(10));
        call(dora, get("/api/reservations/active")).andExpect(jsonPath("$.cancelFreeUntil").value(freeUntil.toString()))
                .andExpect(jsonPath("$.cancelCountsAsNoShow").value(false));
        assertThat(countedToday("dora@uade.edu.ar")).isEqualTo(1);
        at(freeUntil);
        call(dora, get("/api/reservations/active")).andExpect(jsonPath("$.cancelCountsAsNoShow").value(false));
        call(dora, delete("/api/reservations/" + free)).andExpect(status().isNoContent());
        call(dora, delete("/api/reservations/" + free)).andExpect(status().isNoContent()); // idempotente
        call(dora, get("/api/reservations/" + free)).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.seatNumber").doesNotExist());
        assertThat(countedToday("dora@uade.edu.ar")).isZero();
        call(dora, get("/api/me/no-shows")).andExpect(jsonPath("$.recentNoShows").value(0));
        call(dora, get("/api/reservations/active")).andExpect(status().isNoContent());

        // Un segundo después del límite: se libera el lugar pero cuenta como falta y no devuelve el cupo.
        Instant dep1608 = Instant.parse("2026-10-13T16:08:00Z");
        long late = reserveOk(dora, "33", dep1608);
        Instant lateFrom = dep1608.minus(Duration.ofMinutes(10));
        at(lateFrom.plusSeconds(1));
        call(dora, get("/api/reservations/active")).andExpect(jsonPath("$.cancelCountsAsNoShow").value(true));
        call(dora, delete("/api/reservations/" + late)).andExpect(status().isNoContent());
        assertThat(statusOf(late)).isEqualTo(ReservationStatus.CANCELLED_LATE);
        assertThat(reservations.findById(late).orElseThrow().getSeatNumber()).isNull();
        assertThat(countedToday("dora@uade.edu.ar")).isEqualTo(1);
        call(dora, get("/api/me/no-shows")).andExpect(jsonPath("$.recentNoShows").value(1));

        // Desde la hora de salida ya no se puede cancelar.
        Instant dep1612 = Instant.parse("2026-10-13T16:12:00Z");
        at(dep1612.minus(Duration.ofMinutes(5)));
        long toPass = reserveOk(dora, "33", dep1612);
        at(dep1612);
        call(dora, delete("/api/reservations/" + toPass)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTURE_ALREADY_PASSED"));
        assertThat(statusOf(toPass)).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    void anOnTimeCancellationGivesTheDailyQuotaBack() throws Exception {
        String emi = signUp("emi@uade.edu.ar", "3000002");
        at(NOW);
        long departure = departureId(emi, "33", DEP_1606);

        // Ocho reservas por día: si cada cancelación a tiempo no devolviera el cupo, la novena sería rechazada.
        for (int i = 0; i < 8; i++) {
            long id = ((Number) JsonPath.read(body(reserve(emi, departure).andExpect(status().isCreated())), "$.id"))
                    .longValue();
            call(emi, delete("/api/reservations/" + id)).andExpect(status().isNoContent());
        }
        reserve(emi, departure).andExpect(status().isCreated());
        assertThat(countedToday("emi@uade.edu.ar")).isEqualTo(1);
        call(emi, get("/api/reservations/history")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(9))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    // ---------------------------------------------------------------- 5: ascensor fuera de servicio

    @Test
    void takingAnElevatorOutOfServiceCancelsItsReservationsWithoutFaultAndMailsAfterTheCommit() throws Exception {
        String admin = signUp("admin@uade.edu.ar", "4000001", "STUDENT", Role.ADMIN);
        String fer = signUp("fer@uade.edu.ar", "4000002");
        String gus = signUp("gus@uade.edu.ar", "4000003");
        String hugo = signUp("hugo@uade.edu.ar", "4000004");
        at(NOW);
        long dep33 = departureId(fer, "33", DEP_1606);
        long ferRes = reserveOk(fer, "33", DEP_1606);
        long gusRes = reserveOk(gus, "33", DEP_1606);
        long hugoRes = reserveOk(hugo, "34", DEP_1606);
        Map<String, Long> reservationByEmail = Map.of("fer@uade.edu.ar", ferRes, "gus@uade.edu.ar", gusRes);

        // En el momento de mandar cada mail, otro hilo (que solo ve lo ya confirmado) tiene que ver la baja hecha.
        clearInvocations(emailSender);
        List<ReservationStatus> seenFromAnotherThread = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            long id = reservationByEmail.get((String) invocation.getArgument(0));
            seenFromAnotherThread.add(CompletableFuture.supplyAsync(() -> statusOf(id)).get(10, TimeUnit.SECONDS));
            return null;
        }).when(emailSender).send(any(), any(), any());

        call(fer, post("/api/admin/elevators/33/out-of-service")).andExpect(status().isForbidden());
        call(admin, json(post("/api/admin/elevators/33/out-of-service"), "{\"reason\":\"Averia del motor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("33"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.cancelledReservations").value(2));

        assertThat(statusOf(ferRes)).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(statusOf(gusRes)).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(statusOf(hugoRes)).isEqualTo(ReservationStatus.ACTIVE);
        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(emailSender, times(2)).send(to.capture(), anyString(), text.capture());
        assertThat(to.getAllValues()).containsExactlyInAnyOrder("fer@uade.edu.ar", "gus@uade.edu.ar");
        assertThat(text.getAllValues()).allSatisfy(t -> assertThat(t).contains("ascensor 33")
                .contains("No cuenta como falta").doesNotContainIgnoringCase("prioridad"));
        assertThat(seenFromAnotherThread).containsExactly(ReservationStatus.CANCELLED, ReservationStatus.CANCELLED);

        // Sin falta y con el cupo devuelto.
        call(fer, get("/api/me/no-shows")).andExpect(jsonPath("$.recentNoShows").value(0));
        assertThat(countedToday("fer@uade.edu.ar")).isZero();

        // No se ofrece ni se puede reservar; el otro ascensor sigue igual.
        call(fer, get("/api/departures").param("elevator", "33")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        reserve(fer, dep33).andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("ELEVATOR_NOT_BOOKABLE"));
        assertThat(departureId(fer, "34", DEP_1606)).isPositive();

        // Reactivado vuelve a ofrecerse y a recibir reservas; las canceladas no se restauran.
        call(admin, post("/api/admin/elevators/33/in-service")).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        assertThat(departureId(fer, "33", DEP_1606)).isEqualTo(dep33);
        reserve(fer, dep33).andExpect(status().isCreated());
        assertThat(statusOf(gusRes)).isEqualTo(ReservationStatus.CANCELLED);
        verify(emailSender, times(2)).send(any(), any(), any());
    }

    // ---------------------------------------------------------------- 6: simulador

    @Test
    void simulatedKiosksAreRejectedWhileTheSimulatorIsOff() throws Exception {
        String admin = signUp("admin@uade.edu.ar", "5000001", "STUDENT", Role.ADMIN);

        call(admin, json(post("/api/admin/kiosks"), "{\"elevatorId\":\"33\",\"simulated\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SIMULATOR_DISABLED"));
        assertThat(kiosks.count()).isZero();
        call(admin, get("/api/admin/kiosks")).andExpect(jsonPath("$.simulatorEnabled").value(false));
        // Los reales siguen funcionando.
        createKiosk(admin, "33", false);
    }

    @Test
    void simulatedCheckInsAreMarkedAndLeftOutOfKpisAndFaults() throws Exception {
        doReturn(true).when(kioskProps).simulatorEnabled();
        String admin = signUp("admin@uade.edu.ar", "6000001", "STUDENT", Role.ADMIN);
        String ivan = signUp("ivan@uade.edu.ar", "6000002");
        String juana = signUp("juana@uade.edu.ar", "6000003");
        String lola = signUp("lola@uade.edu.ar", "6000004");
        Kiosk simulator = createKiosk(admin, "33", true);
        Kiosk real = createKiosk(admin, "33", false);
        call(admin, get("/api/admin/kiosks")).andExpect(jsonPath("$.simulatorEnabled").value(true));

        at(NOW);
        long ivanRes = reserveOk(ivan, "33", DEP_1600);
        long juanaRes = reserveOk(juana, "33", DEP_1600);
        Instant dep1604 = Instant.parse("2026-10-13T16:04:00Z");
        long lolaRes = reserveOk(lola, "33", dep1604);

        // A tiempo: el simulado queda usado pero marcado SIMULATED; el real, REAL.
        at(DEP_1600.minusSeconds(30));
        checkIn(ivan, simulator).andExpect(status().isCreated()).andExpect(jsonPath("$.source").value("SIMULATED"))
                .andExpect(jsonPath("$.reservationStatus").value("USED"));
        checkIn(juana, real).andExpect(status().isCreated()).andExpect(jsonPath("$.source").value("REAL"))
                .andExpect(jsonPath("$.reservationStatus").value("USED"));

        // Fuera de hora con el simulador: la reserva queda NO_SHOW, pero no es falta.
        at(dep1604.plusSeconds(90));
        assertThat(noShows.markNoShows()).isEqualTo(1);
        checkIn(lola, simulator).andExpect(status().isCreated()).andExpect(jsonPath("$.outcome").value("LATE"))
                .andExpect(jsonPath("$.reservationStatus").value("NO_SHOW"))
                .andExpect(jsonPath("$.source").value("SIMULATED"));
        assertThat(statusOf(ivanRes)).isEqualTo(ReservationStatus.USED);
        assertThat(statusOf(juanaRes)).isEqualTo(ReservationStatus.USED);
        assertThat(statusOf(lolaRes)).isEqualTo(ReservationStatus.NO_SHOW);
        call(lola, get("/api/me/no-shows")).andExpect(status().isOk()).andExpect(jsonPath("$.recentNoShows").value(0))
                .andExpect(jsonPath("$.suspendedUntil").doesNotExist());
        assertThat(audit.findAll()).extracting("action")
                .containsExactlyInAnyOrder("KIOSK_CREATED", "KIOSK_CREATED", "CHECK_IN_SIMULATED",
                        "CHECK_IN_SIMULATED");

        // KpiEvents y el panel solo ven lo real: el check-in de Juana.
        Instant from = NOW.minus(Duration.ofDays(1));
        Instant to = NOW.plus(Duration.ofDays(1));
        assertThat(kpis.checkIns(from, to)).singleElement()
                .satisfies(c -> assertThat(c.getReservationId()).isEqualTo(juanaRes));
        assertThat(kpis.noShows(from, to)).isEmpty();
        assertThat(kpis.reservations(from, to)).extracting(Reservation::getId).containsExactly(juanaRes);
        call(admin, get("/api/admin/kpis").param("period", "TODAY")).andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations").value(1))
                .andExpect(jsonPath("$.checkIns").value(1))
                .andExpect(jsonPath("$.capacity").value(10));
        call(ivan, get("/api/admin/kpis").param("period", "TODAY")).andExpect(status().isForbidden());
        // Para el propio usuario el simulado sí es parte de su historial.
        call(ivan, get("/api/reservations/" + ivanRes)).andExpect(jsonPath("$.checkInOutcome").value("ON_TIME"));
    }

    // ---------------------------------------------------------------- 7: 401 y 403

    @Test
    void protectedEndpointsAnswer401WithoutTokenAnd403WithTheWrongRole() throws Exception {
        String admin = signUp("admin@uade.edu.ar", "7000001", "STUDENT", Role.ADMIN);
        String user = signUp("user@uade.edu.ar", "7000002");
        String reviewer = signUp("rev@uade.edu.ar", "7000003", "STUDENT", Role.REVIEWER);
        String someone = users.findByEmail("user@uade.edu.ar").orElseThrow().getId().toString();
        Kiosk kiosk = createKiosk(admin, "33", false);

        Supplier<MockHttpServletRequestBuilder[]> userEndpoints = () -> new MockHttpServletRequestBuilder[] {
                get("/api/users/me"), get("/api/departures"), get("/api/reservations/active"),
                get("/api/reservations/history"), get("/api/reservations/1"), delete("/api/reservations/1"),
                get("/api/me/no-shows"), get("/api/me/notification-preferences"),
                json(post("/api/reservations"), "{\"departureId\":1,\"originFloor\":0,\"destinationFloor\":8}"),
                json(post("/api/check-ins"), "{\"token\":\"x\"}")};
        Supplier<MockHttpServletRequestBuilder[]> adminEndpoints = () -> new MockHttpServletRequestBuilder[] {
                get("/api/admin/kpis").param("period", "TODAY"), get("/api/admin/shifts"),
                get("/api/admin/kiosks"), json(post("/api/admin/kiosks"), "{\"elevatorId\":\"33\"}"),
                post("/api/admin/kiosks/" + kiosk.id() + "/revoke"), get("/api/admin/elevators"),
                post("/api/admin/elevators/33/out-of-service"), post("/api/admin/elevators/33/in-service"),
                get("/api/admin/teachers/pending"), post("/api/admin/teachers/" + someone + "/approve"),
                get("/api/admin/priority-users"), get("/api/admin/users").param("query", "usuario"),
                json(put("/api/admin/users/" + someone + "/role"), "{\"role\":\"ADMIN\"}")};
        int userCount = userEndpoints.get().length;
        int adminCount = adminEndpoints.get().length;

        for (int i = 0; i < userCount + adminCount; i++) {
            Supplier<MockHttpServletRequestBuilder> request = () -> null;
            final int index = i;
            request = () -> index < userCount ? userEndpoints.get()[index] : adminEndpoints.get()[index - userCount];
            mockMvc.perform(request.get()).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").isString())
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andExpect(jsonPath("$.path").isString())
                    .andExpect(jsonPath("$.timestamp").isString());
            call("token-basura", request.get()).andExpect(status().isUnauthorized());
            // Un secreto de kiosco no es una sesión de usuario.
            call(kiosk.secret(), request.get()).andExpect(status().isUnauthorized());
        }
        for (int i = 0; i < adminCount; i++) {
            for (String token : new String[] {user, reviewer}) {
                call(token, adminEndpoints.get()[i]).andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.status").value(403))
                        .andExpect(jsonPath("$.message").isNotEmpty());
            }
        }
        // El ADMIN sí pasa, y no se tocó nada con los pedidos rechazados.
        call(admin, get("/api/admin/kpis").param("period", "TODAY")).andExpect(status().isOk());
        call(admin, get("/api/admin/elevators")).andExpect(status().isOk());
        assertThat(elevators.findById("33").orElseThrow().isActive()).isTrue();
        assertThat(users.findByEmail("user@uade.edu.ar").orElseThrow().getRole()).isEqualTo(Role.USER);
        assertThat(kiosks.findAll()).singleElement().satisfies(k -> assertThat(k.isRevoked()).isFalse());

        // Las credenciales de kiosco: solo para su endpoint.
        mockMvc.perform(get("/api/kiosks/me/qr-token")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("KIOSK_UNAUTHORIZED"));
        call(user, get("/api/kiosks/me/qr-token")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("KIOSK_UNAUTHORIZED"));
        call(kiosk.secret(), get("/api/kiosks/me/qr-token")).andExpect(status().isOk());

        // Públicos.
        mockMvc.perform(get("/api/ping")).andExpect(status().isOk());
        mockMvc.perform(get("/api/elevators")).andExpect(status().isOk());
        mockMvc.perform(get("/api/no-existe")).andExpect(status().isUnauthorized()).andExpect(content().contentType(
                MediaType.APPLICATION_JSON));
        verify(emailSender, never()).send(eq("user@uade.edu.ar"), anyString(), anyString());
    }
}
