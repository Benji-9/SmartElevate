package com.smartelevate.priority.dto;

import com.smartelevate.user.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AssignRoleRequest(
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "REVIEWER") Role role) {
}
