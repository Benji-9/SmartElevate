package com.smartelevate.turn.dto;

import com.smartelevate.turn.model.CheckInOutcome;
import com.smartelevate.turn.model.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Reserva de un lugar en una salida")
public record ReservationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "55") long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1234") long departureId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "33") String elevator,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Nombre visible del núcleo del ascensor",
                example = "Independencia 2") String coreName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Nombre visible del edificio",
                example = "Independencia") String buildingName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Hora estimada de salida, en UTC",
                example = "2026-10-13T15:58:00Z") Instant departureAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Hasta este instante (inclusive, UTC) "
                + "se cancela sin falta: la salida menos cancellation_deadline_before. Después, cancelar es tardío "
                + "y cuenta como falta; desde la salida ya no se puede cancelar.",
                example = "2026-10-13T15:48:00Z") Instant cancelFreeUntil,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Desde este instante (UTC) se puede "
                + "hacer check-in: la salida menos check_in_opens_before.",
                example = "2026-10-13T15:57:00Z") Instant checkInOpensAt,
        @Schema(description = "Número de lugar, de 1 a la capacidad. Null si la reserva está cancelada.",
                example = "3", nullable = true) Integer seatNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0") int originFloor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "8") int destinationFloor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReservationStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-13T15:40:00Z") Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Si está ACTIVE y cancelarla ahora "
                + "contaría como falta (pasado cancellation_deadline_before). Lo calcula el servidor al responder.")
        boolean cancelCountsAsNoShow,
        @Schema(description = "Resultado del check-in, si hubo: ON_TIME, OTHER_ELEVATOR o LATE (cuenta como "
                + "falta). Incluye los del simulador de admin.", nullable = true) CheckInOutcome checkInOutcome) {
}
