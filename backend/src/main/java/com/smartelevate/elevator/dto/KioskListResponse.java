package com.smartelevate.elevator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Kioscos dados de alta y si el simulador está habilitado")
public record KioskListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "APP_KIOSK_SIMULATOR_ENABLED: si es false no se pueden crear kioscos simulados")
        boolean simulatorEnabled,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<KioskResponse> kiosks) {
}
