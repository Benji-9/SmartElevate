package com.smartelevate.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Access token de la sesión (el refresh va en una cookie HttpOnly, ADR 0019). No lleva rol ni prioridad")
public record TokenResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "JWT corto, va en Authorization: Bearer")
        String accessToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Bearer") String tokenType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "900",
                description = "Segundos de vida del access token")
        long expiresIn) {
}
