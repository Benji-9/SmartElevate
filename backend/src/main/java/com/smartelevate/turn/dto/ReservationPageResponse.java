package com.smartelevate.turn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Página del historial de reservas, de la salida más reciente a la más vieja")
public record ReservationPageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReservationResponse> items,
        @Schema(description = "Cursor opaco para pedir la página siguiente. Null si no hay más.", nullable = true,
                example = "2026-10-13T15:58:00Z_55") String nextCursor) {
}
