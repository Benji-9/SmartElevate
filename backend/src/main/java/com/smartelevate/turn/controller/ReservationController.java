package com.smartelevate.turn.controller;

import com.smartelevate.common.config.OpenApiConfig;
import com.smartelevate.common.error.ApiError;
import com.smartelevate.common.security.CurrentUser;
import com.smartelevate.turn.dto.ReservationResponse;
import com.smartelevate.turn.dto.ReserveRequest;
import com.smartelevate.turn.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER)
@Tag(name = "turn", description = "Salidas de ascensor y turnos")
public class ReservationController {

    private final ReservationService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reserva un lugar en una salida",
            description = "El usuario sale del token y su prioridad se evalúa en el servidor. Los errores de regla "
                    + "traen un code estable: "
                    + "TURN_FULL, OUTSIDE_BOOKING_WINDOW, ACTIVE_LIMIT_REACHED, DAILY_LIMIT_REACHED, "
                    + "ELEVATOR_NOT_BOOKABLE, SAME_FLOOR, FLOOR_OUT_OF_RANGE, FLOOR_NOT_SERVED, "
                    + "LOW_FLOOR_DESTINATION, DEPARTURE_NOT_FOUND. TURN_FULL también cuando los lugares libres están "
                    + "reservados a prioritarios; ELEVATOR_NOT_BOOKABLE también en un ascensor dedicado a otra "
                    + "categoría.")
    @ApiResponse(responseCode = "404", description = "La salida no existe (DEPARTURE_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409",
            description = "Salida llena (TURN_FULL), fuera de ventana, o límite de reservas alcanzado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "422", description = "Pisos o ascensor no válidos para la reserva",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ReservationResponse reserve(@AuthenticationPrincipal CurrentUser user,
                                       @Valid @RequestBody ReserveRequest request) {
        return service.reserve(user.id(), request);
    }

    @GetMapping("/me")
    @Operation(summary = "Lista mis reservas de hoy en adelante (activas y canceladas)",
            description = "Ordenadas por salida, la más tardía primero.")
    public List<ReservationResponse> mine(@AuthenticationPrincipal CurrentUser user) {
        return service.mine(user.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancela una reserva mía y libera el lugar",
            description = "Idempotente. Hasta 1 min antes de la salida (cancellation_deadline_before) queda "
                    + "CANCELLED; después, CANCELLED_LATE (cuenta como falta).")
    @ApiResponse(responseCode = "404", description = "La reserva no existe o es de otro usuario "
            + "(RESERVATION_NOT_FOUND)", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public void cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
        service.cancel(user.id(), id);
    }
}
