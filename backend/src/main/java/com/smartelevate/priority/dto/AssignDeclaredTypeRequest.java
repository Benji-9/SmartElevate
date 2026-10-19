package com.smartelevate.priority.dto;

import com.smartelevate.user.model.DeclaredUserType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AssignDeclaredTypeRequest(
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "TEACHER")
        DeclaredUserType declaredUserType) {
}
