package com.smartelevate.turn.controller;

import com.smartelevate.common.config.OpenApiConfig;
import com.smartelevate.common.security.CurrentUser;
import com.smartelevate.turn.dto.DepartureResponse;
import com.smartelevate.turn.service.DepartureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/departures")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER)
@Tag(name = "turn", description = "Salidas de ascensor y turnos")
public class DepartureController {

    private final DepartureService service;

    @GetMapping
    @Operation(summary = "Lista las salidas reservables ahora (ventana de reserva) con su cupo restante",
            description = "Solo los ascensores que el usuario puede usar según su prioridad vigente (los dedicados "
                    + "solo para su categoría). Genera las salidas que falten de forma idempotente.")
    public List<DepartureResponse> bookable(
            @AuthenticationPrincipal CurrentUser user,
            @Parameter(description = "Código del ascensor", example = "33")
            @RequestParam(required = false) String elevator,
            @Parameter(description = "Código del edificio, sin distinguir mayúsculas", example = "INDEPENDENCIA")
            @RequestParam(required = false) String building) {
        return service.findBookable(user.id(), elevator, building);
    }
}
