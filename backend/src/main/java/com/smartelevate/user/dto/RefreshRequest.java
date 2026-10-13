package com.smartelevate.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Refresh token vigente de la sesión")
public record RefreshRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "b0Yq3k...")
        @NotBlank(message = "Falta el refresh token.")
        String refreshToken) {
}
