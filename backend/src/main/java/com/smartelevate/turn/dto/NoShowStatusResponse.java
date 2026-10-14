package com.smartelevate.turn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Faltas recientes y suspensión (turnos.md §5). Falta = NO_SHOW o CANCELLED_LATE")
public record NoShowStatusResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Faltas en los últimos windowDays días",
                example = "2") int recentNoShows,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Faltas que suspenden",
                example = "3") int threshold,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "7") long windowDays,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "24") long suspensionHours,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Hasta cuándo no puede reservar, en UTC. "
                + "Null si no está suspendido", nullable = true, example = "2026-10-14T15:58:00Z")
        Instant suspendedUntil,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Prioridad vigente: las faltas se "
                + "registran pero no suspenden") boolean exempt) {
}
