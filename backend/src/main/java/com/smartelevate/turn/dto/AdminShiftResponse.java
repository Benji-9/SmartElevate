package com.smartelevate.turn.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Turno de cursada para filtrar los KPIs (kpis.md#filtros). Horas HH:mm de Buenos Aires")
public record AdminShiftResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "MORNING") String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Turno mañana") String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "07:00") String startsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Exclusivo", example = "12:15")
        String endsAt) {
}
