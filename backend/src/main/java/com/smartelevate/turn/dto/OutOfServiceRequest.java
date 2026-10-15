package com.smartelevate.turn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos opcionales al marcar un ascensor fuera de servicio")
public record OutOfServiceRequest(
        @Size(max = 64) @Schema(example = "Avería del motor", description = "Motivo corto, solo para la auditoría")
        String reason) {
}
