package com.smartelevate.turn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Check-in: el token leído del QR del ascensor. El usuario sale de la sesión, no del body.")
public record CheckInRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Contenido del QR, tal cual se leyó")
        @NotBlank @Size(max = 512) String token) {
}
