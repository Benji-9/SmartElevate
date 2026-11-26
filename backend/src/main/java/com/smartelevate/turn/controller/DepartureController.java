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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
                    + "solo para su categoría). Genera las salidas que falten de forma idempotente. Con `core`, "
                    + "`origin` y `destination` (los tres juntos, si falta alguno responde 400) devuelve la oferta "
                    + "por zonas de pisos (ADR 0025): una salida por hora, ya resuelta por el servidor, de los "
                    + "ascensores del núcleo que paran en origen y destino; `elevator` y `building` se ignoran.")
    public List<DepartureResponse> bookable(
            @AuthenticationPrincipal CurrentUser user,
            @Parameter(description = "Código del ascensor", example = "33")
            @RequestParam(required = false) String elevator,
            @Parameter(description = "Código del edificio, sin distinguir mayúsculas", example = "INDEPENDENCIA")
            @RequestParam(required = false) String building,
            @Parameter(description = "Código del núcleo, sin distinguir mayúsculas", example = "L1")
            @RequestParam(required = false) String core,
            @Parameter(description = "Piso de origen", example = "0")
            @RequestParam(required = false) Integer origin,
            @Parameter(description = "Piso de destino", example = "7")
            @RequestParam(required = false) Integer destination) {
        if (core == null && origin == null && destination == null) {
            return service.findBookable(user.id(), elevator, building);
        }
        if (core == null || origin == null || destination == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "core, origin y destination van los tres juntos o ninguno");
        }
        return service.findOffer(user.id(), core, origin, destination);
    }
}
