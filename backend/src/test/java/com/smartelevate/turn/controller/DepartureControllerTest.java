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

    @Test
    void listsBookableDeparturesWithRemainingSeats() throws Exception {
        given(service.findBookable("33", null)).willReturn(List.of(new DepartureResponse(7, "33", "INDEPENDENCIA",
                Instant.parse("2026-10-13T15:58:00Z"), 10, 10, DepartureStatus.SCHEDULED)));

        mockMvc.perform(get("/api/departures").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()).param("elevator", "33"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].elevator").value("33"))
                .andExpect(jsonPath("$[0].building").value("INDEPENDENCIA"))
                .andExpect(jsonPath("$[0].departureAt").value("2026-10-13T15:58:00Z"))
                .andExpect(jsonPath("$[0].capacity").value(10))
                .andExpect(jsonPath("$[0].remainingSeats").value(10))
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"));
    }

    @Test
    void passesBuildingFilterAndReturnsEmptyList() throws Exception {
        given(service.findBookable(null, "LIMA")).willReturn(List.of());

        mockMvc.perform(get("/api/departures").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()).param("building", "LIMA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
