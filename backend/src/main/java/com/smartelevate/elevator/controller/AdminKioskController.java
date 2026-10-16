package com.smartelevate.elevator.controller;

import com.smartelevate.common.config.OpenApiConfig;
import com.smartelevate.common.error.ApiError;
import com.smartelevate.common.security.CurrentUser;
import com.smartelevate.elevator.dto.CreateKioskRequest;
import com.smartelevate.elevator.dto.CreatedKioskResponse;
import com.smartelevate.elevator.dto.KioskListResponse;
import com.smartelevate.elevator.service.KioskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Alta, listado y revocación de kioscos del QR. Solo ADMIN; altas y revocaciones quedan auditadas. */
@RestController
@RequestMapping("/api/admin/kiosks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@Tag(name = "admin-kiosk", description = "Kioscos del QR (solo ADMIN)")
public class AdminKioskController {

    private final KioskService service;

    @GetMapping
    @Operation(summary = "Kioscos dados de alta (sin secretos) y si el simulador está habilitado")
    public KioskListResponse listKiosks() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Da de alta un kiosco para un ascensor",
            description = "Devuelve la credencial del kiosco una sola vez: en la base queda solo su hash.")
    @ApiResponse(responseCode = "201", description = "Kiosco creado, con su secreto")
    @ApiResponse(responseCode = "404", description = "Ascensor inexistente (code ELEVATOR_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409",
            description = "Kiosco simulado con APP_KIOSK_SIMULATOR_ENABLED apagado (code SIMULATOR_DISABLED)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public CreatedKioskResponse createKiosk(@AuthenticationPrincipal CurrentUser admin,
                                            @Valid @RequestBody CreateKioskRequest request) {
        return service.create(admin.id(), request);
    }

    @PostMapping("/{id}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoca un kiosco: deja de funcionar en el acto y sus QR dejan de validar",
            description = "Revocar uno ya revocado no hace nada.")
    @ApiResponse(responseCode = "404", description = "Kiosco inexistente (code KIOSK_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public void revokeKiosk(@AuthenticationPrincipal CurrentUser admin, @PathVariable UUID id) {
        service.revoke(admin.id(), id);
    }
}
