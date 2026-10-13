package com.smartelevate.user.controller;

import com.smartelevate.common.error.ApiError;
import com.smartelevate.user.dto.RegisterRequest;
import com.smartelevate.user.dto.ResendVerificationRequest;
import com.smartelevate.user.dto.VerifyEmailRequest;
import com.smartelevate.user.service.EmailVerificationService;
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
    private final EmailVerificationService emailVerification;

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

    @PostMapping("/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Verifica el email con el token del link",
            description = "El token sirve una sola vez y vence. Si no existe, ya se usó o venció, responde 400 "
                    + "con el motivo en el mensaje. Es POST (y no GET) para que los antivirus de los mails "
                    + "que abren los links no gasten el token.")
    @ApiResponse(responseCode = "400", description = "Token inválido, usado o vencido",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public void verify(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerification.verify(request.token());
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Reenvía el link de verificación",
            description = "Responde 202 exista o no la cuenta, para no revelar qué emails están registrados. "
                    + "Si ya se mandó uno hace poco responde 429 con el header Retry-After.")
    @ApiResponse(responseCode = "429", description = "Ya se mandó un mail hace poco",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        emailVerification.resend(request.email());
    }
}
