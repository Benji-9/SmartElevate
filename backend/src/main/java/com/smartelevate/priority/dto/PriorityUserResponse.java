package com.smartelevate.priority.dto;

import com.smartelevate.priority.model.PriorityCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Usuario con prioridad vigente")
public record PriorityUserResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Ana Pérez") String fullName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "ana@uade.edu.ar") String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1234567") String legajo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "TEACHER o REDUCED_MOBILITY")
        PriorityCategory category) {
}
