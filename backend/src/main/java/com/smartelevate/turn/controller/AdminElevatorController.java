package com.smartelevate.turn.controller;

import com.smartelevate.common.config.OpenApiConfig;
import com.smartelevate.common.error.ApiError;
import com.smartelevate.common.security.CurrentUser;
import com.smartelevate.turn.dto.AdminElevatorResponse;
import com.smartelevate.turn.dto.ElevatorServiceResponse;
import com.smartelevate.turn.dto.OutOfServiceRequest;
import com.smartelevate.turn.service.ElevatorOutageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Estado de servicio de los ascensores (turnos.md §8). Solo ADMIN; cada cambio queda auditado. */
@RestController
@RequestMapping("/api/admin/elevators")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@Tag(name = "admin-elevator", description = "Ascensores fuera de servicio (solo ADMIN)")
public class AdminElevatorController {

    private final ElevatorOutageService service;

    @GetMapping
    @Operation(summary = "Todos los ascensores, en servicio o no, con las reservas que se cancelarían")
    public List<AdminElevatorResponse> list() {
        return service.list();
    }

    @PostMapping("/{code}/out-of-service")
    @Operation(summary = "Marca un ascensor fuera de servicio por avería",
            description = "Cancela sin falta sus reservas activas de salidas futuras (devuelve el cupo diario) y "
                    + "avisa por mail a cada persona. Deja de ofrecerse y de aceptar reservas. Todo o nada. "
                    + "Marcarlo dos veces no hace nada.")
    @ApiResponse(responseCode = "200", description = "Estado resultante",
            content = @Content(schema = @Schema(implementation = ElevatorServiceResponse.class)))
    @ApiResponse(responseCode = "404", description = "Ascensor inexistente (code ELEVATOR_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ElevatorServiceResponse outOfService(@AuthenticationPrincipal CurrentUser admin,
                                                @PathVariable String code,
                                                @Valid @RequestBody(required = false) OutOfServiceRequest request) {
        return service.markOutOfService(admin.id(), code, request == null ? null : request.reason());
    }

    @PostMapping("/{code}/in-service")
    @Operation(summary = "Reactiva un ascensor: vuelve a ofrecerse y a aceptar reservas",
            description = "Las reservas ya canceladas no se restauran. Reactivar uno en servicio no hace nada.")
    @ApiResponse(responseCode = "200", description = "Estado resultante",
            content = @Content(schema = @Schema(implementation = ElevatorServiceResponse.class)))
    @ApiResponse(responseCode = "404", description = "Ascensor inexistente (code ELEVATOR_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ElevatorServiceResponse inService(@AuthenticationPrincipal CurrentUser admin, @PathVariable String code) {
        return service.markInService(admin.id(), code);
    }
}
