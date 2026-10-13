package com.smartelevate.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciales de inicio de sesión")
public record LoginRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "jmartinez@uade.edu.ar")
        @NotBlank(message = "Ingresá tu email.")
        String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "password", example = "una-clave-larga")
        @NotBlank(message = "Ingresá tu contraseña.")
        String password) {
}
