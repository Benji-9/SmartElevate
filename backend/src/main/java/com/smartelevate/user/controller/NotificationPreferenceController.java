package com.smartelevate.user.controller;

import com.smartelevate.common.config.OpenApiConfig;
import com.smartelevate.common.security.CurrentUser;
import com.smartelevate.user.dto.NotificationPreferencesDto;
import com.smartelevate.user.service.NotificationPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/notification-preferences")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER)
@Tag(name = "users", description = "Datos del usuario autenticado")
public class NotificationPreferenceController {

    private final NotificationPreferenceService service;

    @GetMapping
    @Operation(summary = "Preferencias de aviso por email",
            description = "Por defecto todos los avisos están activos. Sin token responde 401.")
    public NotificationPreferencesDto get(@AuthenticationPrincipal CurrentUser user) {
        return service.get(user.id());
    }

    @PutMapping
    @Operation(summary = "Guarda las preferencias de aviso por email",
            description = "Reemplaza las dos preferencias; faltar alguna o mandar null responde 400.")
    public NotificationPreferencesDto update(@AuthenticationPrincipal CurrentUser user,
                                             @Valid @RequestBody NotificationPreferencesDto body) {
        return service.update(user.id(), body);
    }
}
