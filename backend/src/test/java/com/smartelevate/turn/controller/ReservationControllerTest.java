package com.smartelevate.turn.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartelevate.common.config.CorsProperties;
import com.smartelevate.common.security.CurrentUser;
import com.smartelevate.common.security.WithAppSecurity;
import com.smartelevate.common.time.ClockConfig;
import com.smartelevate.turn.dto.ReservationPageResponse;
import com.smartelevate.turn.dto.ReservationResponse;
import com.smartelevate.turn.dto.ReserveRequest;
import com.smartelevate.turn.model.CheckInOutcome;
import com.smartelevate.turn.model.ReservationStatus;
import com.smartelevate.turn.service.ReservationRejectedException;
import com.smartelevate.turn.service.ReservationService;
import com.smartelevate.turn.service.TurnFullException;
import com.smartelevate.user.model.Role;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(ReservationController.class)
@EnableConfigurationProperties(CorsProperties.class)
@Import(ClockConfig.class)
@WithAppSecurity
class ReservationControllerTest {

    private static final UUID USER = UUID.randomUUID();
    private static final String BODY = """
            {"departureId":7,"originFloor":0,"destinationFloor":8}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService service;

    private static RequestPostProcessor asUser() {
        return authentication(new UsernamePasswordAuthenticationToken(new CurrentUser(USER, Role.USER), null,
                List.of()));
    }

    @Test
    void createsTheReservationForTheAuthenticatedUser() throws Exception {
        given(service.reserve(USER, new ReserveRequest(7L, 0, 8))).willReturn(response(55, 3, ReservationStatus.ACTIVE, null));

        mockMvc.perform(post("/api/reservations").with(asUser()).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(55))
                .andExpect(jsonPath("$.departureId").value(7))
                .andExpect(jsonPath("$.elevator").value("33"))
                .andExpect(jsonPath("$.seatNumber").value(3))
                .andExpect(jsonPath("$.cancelFreeUntil").value("2026-10-13T15:48:00Z"))
                .andExpect(jsonPath("$.checkInOpensAt").value("2026-10-13T15:57:00Z"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void ignoresAUserIdSentInTheBody() throws Exception {
        given(service.reserve(any(), any())).willReturn(response(1, 1, ReservationStatus.ACTIVE, null));

        mockMvc.perform(post("/api/reservations").with(asUser()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"departureId":7,"originFloor":0,"destinationFloor":8,"userId":"%s"}"""
                        .formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());

        verify(service).reserve(USER, new ReserveRequest(7L, 0, 8));
    }

    @Test
    void withoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingFieldsReturn400WithViolations() throws Exception {
        mockMvc.perform(post("/api/reservations").with(asUser()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departureId\":7}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.length()").value(2));
    }

    @Test
    void fullTurnReturns409WithCode() throws Exception {
        given(service.reserve(any(), any())).willThrow(new TurnFullException());

        mockMvc.perform(post("/api/reservations").with(asUser()).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("TURN_FULL"))
                .andExpect(jsonPath("$.path").value("/api/reservations"));
    }

    @Test
    void businessRulesMapToTheirStatusAndCode() throws Exception {
        given(service.reserve(any(), any()))
                .willThrow(new ReservationRejectedException(ReservationRejectedException.Reason.SAME_FLOOR, "Mismo piso"))
                .willThrow(new ReservationRejectedException(ReservationRejectedException.Reason.DEPARTURE_NOT_FOUND, "No existe"))
                .willThrow(new ReservationRejectedException(ReservationRejectedException.Reason.DAILY_LIMIT_REACHED, "Tope"));

        mockMvc.perform(post("/api/reservations").with(asUser()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SAME_FLOOR"));
        mockMvc.perform(post("/api/reservations").with(asUser()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPARTURE_NOT_FOUND"));
        mockMvc.perform(post("/api/reservations").with(asUser()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_REACHED"));
    }

    private static ReservationResponse response(long id, Integer seat, ReservationStatus status,
                                                CheckInOutcome outcome) {
        return new ReservationResponse(id, 7, "33", "Independencia 2", "Independencia",
                Instant.parse("2026-10-13T15:58:00Z"), Instant.parse("2026-10-13T15:48:00Z"),
                Instant.parse("2026-10-13T15:57:00Z"), seat, 0, 8, status,
                Instant.parse("2026-10-13T15:55:00Z"), false, outcome);
    }

    @Test
    void activeReturnsMyActiveReservation() throws Exception {
        given(service.active(USER)).willReturn(Optional.of(response(55, 3, ReservationStatus.ACTIVE, null)));

        mockMvc.perform(get("/api/reservations/active").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(55))
                .andExpect(jsonPath("$.coreName").value("Independencia 2"))
                .andExpect(jsonPath("$.buildingName").value("Independencia"))
                .andExpect(jsonPath("$.departureAt").value("2026-10-13T15:58:00Z"))
                .andExpect(jsonPath("$.cancelFreeUntil").value("2026-10-13T15:48:00Z"))
                .andExpect(jsonPath("$.cancelCountsAsNoShow").value(false));
    }

    @Test
    void withoutAnActiveReservationActiveIs204WithoutBody() throws Exception {
        given(service.active(USER)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/reservations/active").with(asUser()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void getReturnsMyReservationWithItsCheckIn() throws Exception {
        given(service.get(USER, 55L)).willReturn(response(55, 3, ReservationStatus.NO_SHOW, CheckInOutcome.LATE));

        mockMvc.perform(get("/api/reservations/55").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"))
                .andExpect(jsonPath("$.checkInOutcome").value("LATE"));
    }

    @Test
    void getSomeoneElsesReservationReturns404() throws Exception {
        given(service.get(USER, 56L)).willThrow(new ReservationRejectedException(
                ReservationRejectedException.Reason.RESERVATION_NOT_FOUND, "La reserva no existe."));

        mockMvc.perform(get("/api/reservations/56").with(asUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void historyPassesTheCursorAndReturnsThePage() throws Exception {
        given(service.history(USER, null)).willReturn(new ReservationPageResponse(
                List.of(response(55, null, ReservationStatus.CANCELLED_LATE, null)), "2026-10-13T15:58:00Z_55"));
        given(service.history(USER, "2026-10-13T15:58:00Z_55")).willReturn(new ReservationPageResponse(List.of(),
                null));

        mockMvc.perform(get("/api/reservations/history").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(55))
                .andExpect(jsonPath("$.items[0].seatNumber").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].status").value("CANCELLED_LATE"))
                .andExpect(jsonPath("$.nextCursor").value("2026-10-13T15:58:00Z_55"));
        mockMvc.perform(get("/api/reservations/history").param("cursor", "2026-10-13T15:58:00Z_55").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void anInvalidCursorIs400WithTheErrorFormat() throws Exception {
        given(service.history(USER, "basura"))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cursor no es válido."));

        mockMvc.perform(get("/api/reservations/history").param("cursor", "basura").with(asUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cursor no es válido."))
                .andExpect(jsonPath("$.path").value("/api/reservations/history"));
    }

    @Test
    void cancelReturns204ForTheAuthenticatedUser() throws Exception {
        mockMvc.perform(delete("/api/reservations/55").with(asUser()))
                .andExpect(status().isNoContent());

        verify(service).cancel(USER, 55L);
    }

    @Test
    void cancellingSomeoneElsesReservationReturns404() throws Exception {
        willThrow(new ReservationRejectedException(ReservationRejectedException.Reason.RESERVATION_NOT_FOUND,
                "La reserva no existe.")).given(service).cancel(USER, 56L);

        mockMvc.perform(delete("/api/reservations/56").with(asUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void cancellingAfterTheDepartureReturns409WithCode() throws Exception {
        willThrow(new ReservationRejectedException(ReservationRejectedException.Reason.DEPARTURE_ALREADY_PASSED,
                "La salida ya pasó: la reserva no se puede cancelar.")).given(service).cancel(USER, 55L);

        mockMvc.perform(delete("/api/reservations/55").with(asUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTURE_ALREADY_PASSED"))
                .andExpect(jsonPath("$.path").value("/api/reservations/55"));
    }

    @Test
    void listAndCancelRequireAToken() throws Exception {
        mockMvc.perform(get("/api/reservations/active")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reservations/history")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reservations/55")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/reservations/55")).andExpect(status().isUnauthorized());
    }
}
