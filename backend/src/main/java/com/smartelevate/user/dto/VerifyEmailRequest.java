package com.smartelevate.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Token del link que llegó por mail")
public record VerifyEmailRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Zk3pQ1x9...")
        @NotBlank(message = "Falta el token.")
        String token) {
}
