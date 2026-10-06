package com.smartelevate.elevator.controller;

import com.smartelevate.elevator.dto.BuildingResponse;
import com.smartelevate.elevator.dto.ElevatorResponse;
import com.smartelevate.elevator.service.ElevatorQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Público y de solo lectura: son datos del edificio, no personales (#120 decide si pasa a autenticado).
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "elevator", description = "Edificios, núcleos y ascensores (solo lectura)")
public class ElevatorController {

    private final ElevatorQueryService service;

    @GetMapping("/elevators")
    @Operation(summary = "Lista los ascensores activos con los pisos donde paran")
    public List<ElevatorResponse> elevators(
            @Parameter(description = "Código del edificio, sin distinguir mayúsculas", example = "INDEPENDENCIA")
            @RequestParam(required = false) String building,
            @Parameter(description = "Solo los ascensores que paran en este piso (0 = PB)", example = "11")
            @RequestParam(required = false) Integer floor) {
        return service.findActive(building, floor);
    }

    @GetMapping("/buildings")
    @Operation(summary = "Lista los edificios con su rango de pisos y sus núcleos")
    public List<BuildingResponse> buildings() {
        return service.findBuildings();
    }
}
