package com.smartelevate.user.controller;

import com.smartelevate.common.config.OpenApiConfig;
import com.smartelevate.common.security.CurrentUser;
import com.smartelevate.user.dto.UserResponse;
import com.smartelevate.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER)
@Tag(name = "users", description = "Datos del usuario autenticado")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Devuelve el usuario de la sesión",
            description = "El rol se lee de la base y la prioridad es la vigente ahora (ADR 0009): ninguno sale "
                    + "de un claim del JWT. Sin token o con uno inválido responde 401.")
    public UserResponse me(@AuthenticationPrincipal CurrentUser user) {
        return userService.me(user.id());
    }
}
