package com.smartelevate.elevator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Kiosco del QR, sin su secreto")
public record KioskResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "33") String elevatorId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean simulated,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(description = "Último pedido de token. Null si nunca se conectó", nullable = true)
        Instant lastSeenAt,
        @Schema(description = "Null mientras está activo", nullable = true) Instant revokedAt) {
}
