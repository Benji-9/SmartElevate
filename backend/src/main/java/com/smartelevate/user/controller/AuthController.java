package com.smartelevate.user.controller;

import com.smartelevate.common.error.ApiError;
import com.smartelevate.user.dto.RegisterRequest;
import com.smartelevate.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "auth", description = "Alta de cuentas de usuario")
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una cuenta común, sin verificar",
            description = "El rol y la prioridad no se piden ni se aceptan: todo usuario nace común. "
                    + "Si el email o el legajo ya tienen cuenta, responde 409 con el campo que chocó.")
    @ApiResponse(responseCode = "409", description = "El email o el legajo ya tienen cuenta",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public void register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
    }
}
