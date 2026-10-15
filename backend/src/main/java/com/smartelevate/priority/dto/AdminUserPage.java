package com.smartelevate.priority.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Página de usuarios; pedir la siguiente con cursor=nextCursor")
public record AdminUserPage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AdminUserResponse> items,
        @Schema(description = "Cursor de la página siguiente; null si no hay más", nullable = true)
        UUID nextCursor) {
}
