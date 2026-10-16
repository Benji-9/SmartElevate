package com.smartelevate.turn.dto;

import com.smartelevate.elevator.model.ElevatorUsage;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ascensor en el panel de admin, esté o no en servicio")
public record AdminElevatorResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "33") String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Independencia 2") String coreName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "INDEPENDENCIA") String building,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ElevatorUsage usage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "false = fuera de servicio") boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "4",
                description = "Reservas activas de salidas futuras: las que se cancelan al marcarlo fuera de servicio")
        int upcomingReservations) {
}
