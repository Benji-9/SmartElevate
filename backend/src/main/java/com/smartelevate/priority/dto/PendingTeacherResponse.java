package com.smartelevate.priority.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Docente declarado que espera la aprobación de un ADMIN")
public record PendingTeacherResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Ana Pérez") String fullName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "ana@uade.edu.ar") String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1234567") String legajo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Alta de la cuenta, en UTC")
        Instant registeredAt) {
}
