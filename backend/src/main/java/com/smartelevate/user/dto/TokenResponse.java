package com.smartelevate.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Par de tokens de la sesión. El access token no lleva rol ni prioridad")
public record TokenResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "JWT corto, va en Authorization: Bearer")
        String accessToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Opaco y de un solo uso: cada refresh devuelve uno nuevo")
        String refreshToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Bearer") String tokenType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "900",
                description = "Segundos de vida del access token")
        long expiresIn) {
}
