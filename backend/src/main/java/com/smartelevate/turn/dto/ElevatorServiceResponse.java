package com.smartelevate.turn.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estado de servicio de un ascensor después del cambio")
public record ElevatorServiceResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "33") String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "4",
                description = "Reservas canceladas por este pedido (0 si ya estaba en ese estado o al reactivar)")
        int cancelledReservations) {
}
