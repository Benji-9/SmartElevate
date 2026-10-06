package com.smartelevate.elevator.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** La API de ascensores contra el seed real de V2__elevators.sql (H2 + Flyway). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ElevatorApiSeedTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsAllActiveElevatorsSortedByBuildingCoreAndCode() throws Exception {
        mockMvc.perform(get("/api/elevators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(18)))
                .andExpect(jsonPath("$[0].code").value("05"))
                .andExpect(jsonPath("$[0].core").value("IND1"))
                .andExpect(jsonPath("$[0].usage").value("TEACHERS"))
                .andExpect(jsonPath("$[0].floors", contains(-4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)));
    }

    @Test
    void floorOneInIndependenciaSkipsInd2() throws Exception {
        mockMvc.perform(get("/api/elevators").param("building", "INDEPENDENCIA").param("floor", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("05", "06")));
    }

    @Test
    void floorElevenIsOnlyInd2() throws Exception {
        mockMvc.perform(get("/api/elevators").param("floor", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", contains("33", "34")))
                .andExpect(jsonPath("$[0].floors", contains(-4, -3, -2, -1, 0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)));
    }

    @Test
    void buildingFilterOnlyReturnsThatBuilding() throws Exception {
        mockMvc.perform(get("/api/elevators").param("building", "LIMA").param("floor", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].building", everyItem(is("LIMA"))))
                // L2 alta (35, 36) no para en el −1.
                .andExpect(jsonPath("$[*].code", not(hasItem("35"))))
                .andExpect(jsonPath("$", hasSize(12)));
    }

    @Test
    void buildingFilterIgnoresCase() throws Exception {
        mockMvc.perform(get("/api/elevators").param("building", "lima").param("floor", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(12)));
        mockMvc.perform(get("/api/elevators").param("building", "Independencia").param("floor", "11"))
                .andExpect(jsonPath("$[*].code", contains("33", "34")))
                .andExpect(jsonPath("$[0].coreName").value("Independencia 2"));
    }

    @Test
    void unknownBuildingReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/elevators").param("building", "CHILE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listsBuildingsWithRangeAndCores() throws Exception {
        mockMvc.perform(get("/api/buildings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", contains("INDEPENDENCIA", "LIMA")))
                .andExpect(jsonPath("$[0].minFloor").value(-4))
                .andExpect(jsonPath("$[0].maxFloor").value(11))
                .andExpect(jsonPath("$[0].cores[*].code", contains("IND1", "IND2")))
                .andExpect(jsonPath("$[1].maxFloor").value(10))
                .andExpect(jsonPath("$[1].cores[*].code", contains("L1", "L2", "L3")))
                .andExpect(jsonPath("$[0].cores[*].name", contains("Independencia 1", "Independencia 2")))
                .andExpect(jsonPath("$[1].cores[*].name", contains("Lima 1", "Lima 2", "Lima 3")));
    }
}
