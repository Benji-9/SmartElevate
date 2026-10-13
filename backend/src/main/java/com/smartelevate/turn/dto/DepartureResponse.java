package com.smartelevate.turn.dto;

import com.smartelevate.turn.model.DepartureStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Salida de un ascensor reservable ahora, con su cupo restante")
public record DepartureResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1234") long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "33") String elevator,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "INDEPENDENCIA") String building,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Hora estimada de salida, en UTC",
                example = "2026-10-13T15:56:00Z") Instant departureAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "10") int capacity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Lugares libres", example = "10")
        int remainingSeats,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DepartureStatus status) {
}
