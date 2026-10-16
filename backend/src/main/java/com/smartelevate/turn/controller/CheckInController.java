package com.smartelevate.turn.controller;

import com.smartelevate.common.config.OpenApiConfig;
import com.smartelevate.common.error.ApiError;
import com.smartelevate.common.security.CurrentUser;
import com.smartelevate.turn.dto.CheckInRequest;
import com.smartelevate.turn.dto.CheckInResponse;
import com.smartelevate.turn.service.CheckInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/check-ins")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER)
@Tag(name = "turn", description = "Salidas de ascensor y turnos")
public class CheckInController {

    private final CheckInService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Hace check-in con el QR del ascensor",
            description = "El usuario sale de la sesión; el ascensor y el kiosco, del token firmado. Se acepta desde "
                    + "check_in_opens_before antes hasta check_in_closes_after después de la salida (hoy −1 min a "
                    + "+2 min), una sola vez por reserva. Pasado no_show_after (+60 s) el resultado es LATE y la "
                    + "reserva queda NO_SHOW. Los rechazos traen un code estable: INVALID_QR_TOKEN, "
                    + "NO_ACTIVE_RESERVATION, OUTSIDE_CHECK_IN_WINDOW, ALREADY_CHECKED_IN.")
    @ApiResponse(responseCode = "400", description = "Body inválido, o QR inválido, vencido o de un kiosco revocado "
            + "(INVALID_QR_TOKEN, sin decir cuál)", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Sin turno (NO_ACTIVE_RESERVATION), fuera de la ventana "
            + "(OUTSIDE_CHECK_IN_WINDOW) o check-in ya registrado (ALREADY_CHECKED_IN)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public CheckInResponse checkIn(@AuthenticationPrincipal CurrentUser user,
                                   @Valid @RequestBody CheckInRequest request) {
        return service.checkIn(user.id(), request.token());
    }
}
