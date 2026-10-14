package com.smartelevate.turn.dto;

import com.smartelevate.turn.model.CheckInOutcome;
import com.smartelevate.turn.model.CheckInSource;
import com.smartelevate.turn.model.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Check-in registrado")
public record CheckInResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "55") long reservationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ON_TIME (cumplida), OTHER_ELEVATOR o "
                + "LATE (fuera de hora: cuenta como falta)") CheckInOutcome outcome,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Estado de la reserva después del "
                + "check-in: USED, o NO_SHOW si fue LATE") ReservationStatus reservationStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ascensor escaneado", example = "34")
        String elevator,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ascensor de la reserva",
                example = "33") String reservedElevator,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Hora estimada de salida, en UTC",
                example = "2026-10-13T15:58:00Z") Instant departureAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-13T15:57:40Z") Instant checkedInAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "SIMULATED si el QR vino del "
                + "simulador de admin") CheckInSource source) {
}
