package com.smartelevate.elevator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Kiosco recién creado. Es la única vez que la API devuelve el secreto")
public record CreatedKioskResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "33") String elevatorId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean simulated,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Credencial del kiosco (Authorization: Bearer <secret>). No se puede volver a consultar",
                example = "q3Jx0b5yT9mV2cK8pL4wZ7nR1sA6dF0hG3jE5uI8oYk")
        String secret) {
}
