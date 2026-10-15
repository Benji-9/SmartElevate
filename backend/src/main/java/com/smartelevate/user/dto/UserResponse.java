package com.smartelevate.user.dto;

import com.smartelevate.priority.model.PriorityCategory;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Usuario de la sesión. Rol y prioridad salen del servidor, nunca del token")
public record UserResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "ana@uade.edu.ar") String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Ana Pérez") String fullName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1234567") String legajo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Role role,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Prioridad vigente, solo para mostrar: se vuelve a evaluar al reservar")
        PriorityCategory priority,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Lo que declaró al registrarse. Informativo: no da rol ni prioridad")
        DeclaredUserType declaredUserType) {
}
