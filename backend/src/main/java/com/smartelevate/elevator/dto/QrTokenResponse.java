package com.smartelevate.elevator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Token firmado que el kiosco muestra como QR")
public record QrTokenResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Contenido del QR (opaco para el kiosco)",
                example = "MzN8NmY5ZTRkM2EtM2I0Ny00YTVmLWI5ZjEtM2Q1ZTZmN2E4YjljfDE3NjA0NTYwMzA.c2lnbmF0dXJl")
        String token,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Vencimiento en UTC: a esta hora el kiosco pide el siguiente",
                example = "2026-10-14T15:40:30Z")
        Instant expiresAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Ascensor de la credencial: el kiosco avisa si no coincide con el de su URL",
                example = "33")
        String elevatorId) {
}
