package com.smartelevate.user.controller;

import com.smartelevate.common.error.ApiError;
import com.smartelevate.user.dto.LoginRequest;
import com.smartelevate.user.dto.RefreshRequest;
import com.smartelevate.user.dto.RegisterRequest;
import com.smartelevate.user.dto.TokenResponse;
import com.smartelevate.user.service.AuthService;
import com.smartelevate.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "auth", description = "Alta de cuentas y sesión (login, refresh, logout)")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una cuenta común, sin verificar",
            description = "El rol y la prioridad no se piden ni se aceptan: todo usuario nace común. "
                    + "Si el email o el legajo ya tienen cuenta, responde 409 con el campo que chocó.")
    @ApiResponse(responseCode = "409", description = "El email o el legajo ya tienen cuenta",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public void register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesión y devuelve access y refresh token",
            description = "Las credenciales incorrectas dan 401 sin distinguir si el email existe. "
                    + "Con la clave correcta pero el email sin verificar responde 403.")
    @ApiResponse(responseCode = "401", description = "Email o contraseña incorrectos",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "El email institucional todavía no se verificó",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Canjea un refresh token por un par nuevo (rotación)",
            description = "El refresh token es de un solo uso: si uno ya canjeado se vuelve a presentar, "
                    + "se revoca toda la sesión.")
    @ApiResponse(responseCode = "401", description = "Refresh token inválido, vencido, revocado o ya usado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cierra la sesión revocando el refresh token",
            description = "Idempotente: un token desconocido tampoco da error.")
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }
}
