package com.smartelevate.turn.controller;

import com.smartelevate.common.config.OpenApiConfig;
import com.smartelevate.common.security.CurrentUser;
import com.smartelevate.turn.dto.NoShowStatusResponse;
import com.smartelevate.turn.service.NoShowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER)
@Tag(name = "turn", description = "Salidas de ascensor y turnos")
public class NoShowController {

    private final NoShowService service;

    @GetMapping("/no-shows")
    @Operation(summary = "Mis faltas recientes y si estoy suspendido",
            description = "Falta = NO_SHOW o CANCELLED_LATE. threshold faltas en windowDays días suspenden "
                    + "suspensionHours horas: mientras tanto, reservar responde NO_SHOW_SUSPENDED. Con prioridad "
                    + "vigente, exempt = true y nunca hay suspensión.")
    public NoShowStatusResponse mine(@AuthenticationPrincipal CurrentUser user) {
        return service.status(user.id());
    }
}
