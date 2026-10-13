package com.smartelevate.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Cuenta a la que reenviar el link de verificación")
public record ResendVerificationRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "jmartinez@uade.edu.ar")
        @NotBlank(message = "Ingresá tu email.")
        String email) {
}
