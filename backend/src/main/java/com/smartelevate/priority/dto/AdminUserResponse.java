package com.smartelevate.priority.dto;

import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Usuario encontrado por el buscador del panel admin (sin datos sensibles)")
public record AdminUserResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Ana Pérez") String fullName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "ana@uade.edu.ar") String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1234567") String legajo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Rol actual") Role role,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Estudiante o docente (lo declarado al registrarse)") DeclaredUserType userType) {
}
