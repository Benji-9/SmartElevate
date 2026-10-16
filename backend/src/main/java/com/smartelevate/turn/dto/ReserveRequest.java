package com.smartelevate.turn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Pedido de reserva. El usuario sale del token, no del body.")
public record ReserveRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1234") @NotNull Long departureId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0") @NotNull Integer originFloor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "8") @NotNull Integer destinationFloor) {
}
