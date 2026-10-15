package com.smartelevate.turn.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartelevate.common.config.CorsProperties;
import com.smartelevate.common.security.CurrentUser;
import com.smartelevate.common.security.WithAppSecurity;
import com.smartelevate.common.time.ClockConfig;
import com.smartelevate.turn.dto.ReservationResponse;
import com.smartelevate.turn.dto.ReserveRequest;
import com.smartelevate.turn.model.ReservationStatus;
import com.smartelevate.turn.service.ReservationRejectedException;
import com.smartelevate.turn.service.ReservationService;
import com.smartelevate.turn.service.TurnFullException;
import com.smartelevate.user.model.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

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
        given(service.reserve(USER, new ReserveRequest(7L, 0, 8))).willReturn(new ReservationResponse(55, 7, "33",
                Instant.parse("2026-10-13T15:58:00Z"), Instant.parse("2026-10-13T15:48:00Z"), 3, 0, 8,
                ReservationStatus.ACTIVE, Instant.parse("2026-10-13T15:55:00Z")));

        mockMvc.perform(post("/api/reservations").with(asUser()).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(55))
                .andExpect(jsonPath("$.departureId").value(7))
                .andExpect(jsonPath("$.elevator").value("33"))
                .andExpect(jsonPath("$.seatNumber").value(3))
                .andExpect(jsonPath("$.cancelFreeUntil").value("2026-10-13T15:48:00Z"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void ignoresAUserIdSentInTheBody() throws Exception {
        given(service.reserve(any(), any())).willReturn(new ReservationResponse(1, 7, "33", Instant.EPOCH, Instant.EPOCH, 1, 0, 8,
                ReservationStatus.ACTIVE, Instant.EPOCH));

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

    @Test
    void listsMyReservationsIncludingCancelledOnesWithoutSeat() throws Exception {
        given(service.mine(USER)).willReturn(List.of(new ReservationResponse(55, 7, "33",
                Instant.parse("2026-10-13T15:58:00Z"), Instant.parse("2026-10-13T15:48:00Z"), null, 0, 8,
                ReservationStatus.CANCELLED_LATE, Instant.EPOCH)));

        mockMvc.perform(get("/api/reservations/me").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(55))
                .andExpect(jsonPath("$[0].departureAt").value("2026-10-13T15:58:00Z"))
                .andExpect(jsonPath("$[0].cancelFreeUntil").value("2026-10-13T15:48:00Z"))
                .andExpect(jsonPath("$[0].seatNumber").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].status").value("CANCELLED_LATE"));
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
        mockMvc.perform(get("/api/reservations/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/reservations/55")).andExpect(status().isUnauthorized());
    }
}
