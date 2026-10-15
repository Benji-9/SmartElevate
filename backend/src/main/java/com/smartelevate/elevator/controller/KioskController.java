package com.smartelevate.elevator.controller;

import com.smartelevate.common.config.OpenApiConfig;
import com.smartelevate.common.error.ApiError;
import com.smartelevate.common.security.CurrentKiosk;
import com.smartelevate.elevator.dto.QrTokenResponse;
import com.smartelevate.elevator.service.KioskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints que usa el propio kiosco, autenticado con su credencial de dispositivo (ver KioskSecurityConfig). */
@RestController
@RequestMapping("/api/kiosks")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.KIOSK)
@Tag(name = "kiosk", description = "Kiosco del QR del ascensor (credencial de dispositivo)")
public class KioskController {

    private final KioskService service;

    @GetMapping("/me/qr-token")
    @Operation(summary = "Token del QR del ascensor del kiosco",
            description = "El ascensor sale de la credencial, no de un parámetro. El token rota cada ~30 s: el "
                    + "kiosco pide el siguiente al llegar a expiresAt. Hay un tope de pedidos por kiosco.")
    @ApiResponse(responseCode = "200", description = "Token vigente")
    @ApiResponse(responseCode = "401", description = "Sin credencial de kiosco, inválida o revocada",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "429", description = "Demasiados pedidos; Retry-After dice cuántos segundos esperar",
            headers = @Header(name = "Retry-After", schema = @Schema(type = "integer")),
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public QrTokenResponse qrToken(@AuthenticationPrincipal CurrentKiosk kiosk) {
        return service.issueQrToken(kiosk);
    }
}
