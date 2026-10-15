package com.smartelevate.turn.controller;

import com.smartelevate.common.config.OpenApiConfig;
import com.smartelevate.common.error.ApiError;
import com.smartelevate.turn.dto.AdminKpisResponse;
import com.smartelevate.turn.dto.AdminPeriod;
import com.smartelevate.turn.dto.AdminShiftResponse;
import com.smartelevate.turn.service.AdminKpiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** KPIs de congestión del panel. Solo ADMIN. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@Tag(name = "admin-kpis", description = "KPIs de congestión del panel (solo ADMIN)")
public class AdminKpiController {

    private final AdminKpiService service;

    @GetMapping("/kpis")
    @Operation(summary = "KPIs del período, con los del período anterior de igual largo",
            description = "Período en días de Buenos Aires que terminan hoy (TODAY = hoy, WEEK = últimos 7, "
                    + "MONTH = últimos 30). Sin las reservas con check-in simulado.")
    @ApiResponse(responseCode = "400", description = "Período, sede o turno de cursada inválidos",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AdminKpisResponse kpis(
            @RequestParam AdminPeriod period,
            @Parameter(description = "Código del edificio; sin él, todas las sedes", example = "LIMA")
            @RequestParam(required = false) String buildingId,
            @Parameter(description = "Turno de cursada (GET /api/admin/shifts); sin él, todo el día",
                    example = "MORNING")
            @RequestParam(required = false) String shiftId) {
        return service.kpis(period, buildingId, shiftId);
    }

    @GetMapping("/shifts")
    @Operation(summary = "Turnos de cursada para filtrar los KPIs")
    public List<AdminShiftResponse> shifts() {
        return service.shifts();
    }
}
