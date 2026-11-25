package com.smartelevate.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Los máximos son solo un tope técnico (#345): el login no aplica la política de contraseña, pero tampoco tiene
 * sentido normalizar y hashear con Argon2id un texto de megabytes. La política real (hasta 64 caracteres) la fijan
 * el registro y el reset.
 */
@Schema(description = "Credenciales de inicio de sesión")
public record LoginRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "jmartinez@uade.edu.ar")
        @NotBlank(message = "Ingresá tu email.")
        // min = 1 para que el contrato siga diciendo minLength 1: springdoc toma el mínimo de @Size, no de @NotBlank.
        @Size(min = 1, max = 254, message = "El email tiene que tener entre 1 y 254 caracteres.")
        String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "password", example = "una-clave-larga")
        @NotBlank(message = "Ingresá tu contraseña.")
        @Size(min = 1, max = 256, message = "La contraseña tiene que tener entre 1 y 256 caracteres.")
        String password) {
}
