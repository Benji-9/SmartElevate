package com.smartelevate.turn.dto;

import com.smartelevate.turn.model.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Reserva de un lugar en una salida")
public record ReservationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "55") long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1234") long departureId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "33") String elevator,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Hora estimada de salida, en UTC",
                example = "2026-10-13T15:58:00Z") Instant departureAt,
        @Schema(description = "Número de lugar, de 1 a la capacidad. Null si la reserva está cancelada.",
                example = "3", nullable = true) Integer seatNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0") int originFloor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "8") int destinationFloor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReservationStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-13T15:40:00Z") Instant createdAt) {
}
