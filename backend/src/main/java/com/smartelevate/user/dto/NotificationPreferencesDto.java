package com.smartelevate.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Qué avisos por email quiere recibir el usuario. Sirve de respuesta y de cuerpo del PUT (se reemplaza entero). */
@Schema(name = "NotificationPreferences", description = "Avisos por email que el usuario quiere recibir")
public record NotificationPreferencesDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Recordatorio un rato antes de la salida de su turno", example = "true")
        @NotNull Boolean departureReminder,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Aviso si su reserva se cancela porque el ascensor sale de servicio", example = "true")
        @NotNull Boolean elevatorOutOfService) {
}
