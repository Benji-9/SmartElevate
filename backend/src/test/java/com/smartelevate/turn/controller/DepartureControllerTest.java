package com.smartelevate.turn.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartelevate.common.config.CorsProperties;
import com.smartelevate.common.time.ClockConfig;
import com.smartelevate.turn.dto.DepartureResponse;
import com.smartelevate.turn.model.DepartureStatus;
import com.smartelevate.turn.service.DepartureService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DepartureController.class)
@EnableConfigurationProperties(CorsProperties.class)
@Import(ClockConfig.class)
@com.smartelevate.common.security.WithAppSecurity
class DepartureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DepartureService service;

    private static final java.util.UUID USER = java.util.UUID.randomUUID();

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asUser() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        new com.smartelevate.common.security.CurrentUser(USER, com.smartelevate.user.model.Role.USER),
                        null, List.of()));
    }

    @Test
    void requiresAToken() throws Exception {
        mockMvc.perform(get("/api/departures")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsBookableDeparturesWithRemainingSeats() throws Exception {
        given(service.findBookable(USER, "33", null)).willReturn(List.of(new DepartureResponse(7, "33", "INDEPENDENCIA",
                Instant.parse("2026-10-13T15:58:00Z"), 10, 10, 0,
                Instant.parse("2026-10-13T15:56:00Z"), DepartureStatus.SCHEDULED)));

        mockMvc.perform(get("/api/departures").with(asUser()).param("elevator", "33"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].elevator").value("33"))
                .andExpect(jsonPath("$[0].building").value("INDEPENDENCIA"))
                .andExpect(jsonPath("$[0].departureAt").value("2026-10-13T15:58:00Z"))
                .andExpect(jsonPath("$[0].capacity").value(10))
                .andExpect(jsonPath("$[0].remainingSeats").value(10))
                .andExpect(jsonPath("$[0].bookedSeats").value(0))
                .andExpect(jsonPath("$[0].bookingClosesAt").value("2026-10-13T15:56:00Z"))
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"));
    }

    @Test
    void passesBuildingFilterAndReturnsEmptyList() throws Exception {
        given(service.findBookable(USER, null, "LIMA")).willReturn(List.of());

        mockMvc.perform(get("/api/departures").with(asUser()).param("building", "LIMA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
