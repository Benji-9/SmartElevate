package com.smartelevate.elevator.controller;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartelevate.common.config.CorsProperties;
import com.smartelevate.common.time.ClockConfig;
import com.smartelevate.elevator.dto.BuildingResponse;
import com.smartelevate.elevator.dto.ElevatorResponse;
import com.smartelevate.elevator.model.ElevatorUsage;
import com.smartelevate.elevator.service.ElevatorQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ElevatorController.class)
@EnableConfigurationProperties(CorsProperties.class)
@Import(ClockConfig.class)
class ElevatorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ElevatorQueryService service;

    @Test
    void listsElevatorsWithoutFilters() throws Exception {
        given(service.findActive(null, null)).willReturn(List.of(
                new ElevatorResponse("33", "IND2", "INDEPENDENCIA", ElevatorUsage.COMMON, true, List.of(-4, 0, 2, 11))));

        mockMvc.perform(get("/api/elevators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("33"))
                .andExpect(jsonPath("$[0].core").value("IND2"))
                .andExpect(jsonPath("$[0].building").value("INDEPENDENCIA"))
                .andExpect(jsonPath("$[0].usage").value("COMMON"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].floors").value(contains(-4, 0, 2, 11)));
    }

    @Test
    void passesFiltersToService() throws Exception {
        given(service.findActive("INDEPENDENCIA", 11)).willReturn(List.of());

        mockMvc.perform(get("/api/elevators").param("building", "INDEPENDENCIA").param("floor", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        verify(service).findActive("INDEPENDENCIA", 11);
    }

    @Test
    void nonNumericFloorIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/elevators").param("floor", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/elevators"))
                .andExpect(jsonPath("$.violations[0].field").value("floor"));
        verify(service, never()).findActive(any(), any());
    }

    @Test
    void listsBuildingsWithCores() throws Exception {
        given(service.findBuildings()).willReturn(List.of(new BuildingResponse("LIMA", "Lima", -4, 10,
                List.of(new BuildingResponse.CoreResponse("L1"), new BuildingResponse.CoreResponse("L2")))));

        mockMvc.perform(get("/api/buildings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("LIMA"))
                .andExpect(jsonPath("$[0].name").value("Lima"))
                .andExpect(jsonPath("$[0].minFloor").value(-4))
                .andExpect(jsonPath("$[0].maxFloor").value(10))
                .andExpect(jsonPath("$[0].cores[1].code").value("L2"));
    }
}
