package com.smartelevate.elevator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Alta de un kiosco del QR para un ascensor")
public record CreateKioskRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Código del ascensor", example = "33")
        @NotBlank @Size(max = 8) String elevatorId,
        @Schema(description = "Kiosco del simulador de admin. Solo con APP_KIOSK_SIMULATOR_ENABLED; default false")
        Boolean simulated) {
}
