package com.smartelevate.turn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Congestión ahora por núcleo, a partir de las salidas y reservas próximas (sin datos personales)")
public record CongestionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Momento del cálculo, en UTC",
                example = "2026-10-13T15:56:00Z") Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Cada cuánto volver a consultar: una "
                + "franja (salida de ascensor)", example = "120") long refreshAfterSeconds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Core> cores) {

    @Schema(name = "CoreCongestion", description = "Congestión de un núcleo")
    public record Core(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L1") String coreId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Lima 1") String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CongestionLevel level,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Lugares reservados / capacidad de "
                    + "las salidas de la ventana de reserva, en % (0 si no hay salidas)", example = "62.5")
            double occupancyPercent) {
    }
}
