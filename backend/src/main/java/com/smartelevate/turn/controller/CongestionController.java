package com.smartelevate.turn.controller;

import com.smartelevate.common.config.OpenApiConfig;
import com.smartelevate.turn.dto.CongestionResponse;
import com.smartelevate.turn.service.CongestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/congestion")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER)
@Tag(name = "turn", description = "Salidas de ascensor y turnos")
public class CongestionController {

    private final CongestionService service;

    @GetMapping
    @Operation(summary = "Congestión ahora por núcleo",
            description = "Lugares reservados / capacidad de las salidas de la ventana de reserva. El nivel sale de "
                    + "los umbrales congestion_medium_from y congestion_high_from de app_config.")
    public CongestionResponse now() {
        return service.now();
    }
}
