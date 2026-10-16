package com.smartelevate.user.controller;

import com.smartelevate.common.error.ApiError;
import com.smartelevate.common.security.RefreshCookie;
import com.smartelevate.user.dto.SessionTokens;
import com.smartelevate.user.dto.ForgotPasswordRequest;
import com.smartelevate.user.dto.ResetPasswordRequest;
import com.smartelevate.user.service.PasswordResetService;
import com.smartelevate.user.dto.LoginRequest;
import com.smartelevate.user.dto.RegisterRequest;
import com.smartelevate.user.dto.TokenResponse;
import com.smartelevate.user.service.AuthService;
import com.smartelevate.user.dto.ResendVerificationRequest;
import com.smartelevate.user.dto.VerifyEmailRequest;
import com.smartelevate.user.service.EmailVerificationService;
import com.smartelevate.user.service.InvalidRefreshTokenException;
import com.smartelevate.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "auth", description = "Alta de cuentas y sesión (login, refresh, logout)")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;
    private final EmailVerificationService emailVerification;
    private final PasswordResetService passwordReset;
    private final RefreshCookie refreshCookie;

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

    @PostMapping("/login")
    @Operation(summary = "Inicia sesión y devuelve el access token y setea el refresh en una cookie HttpOnly",
            description = "Las credenciales incorrectas dan 401 sin distinguir si el email existe. "
                    + "Con la clave correcta pero el email sin verificar responde 403. Demasiados intentos fallidos "
                    + "para la cuenta o la IP dan 429 con el header Retry-After (ventana deslizante, ADR 0015).")
    @ApiResponse(responseCode = "200", description = "Access token; Set-Cookie con el refresh",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "401", description = "Email o contraseña incorrectos",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "El email institucional todavía no se verificó",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "429", description = "Demasiados intentos fallidos; Retry-After dice cuántos segundos "
            + "esperar", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return withCookie(authService.login(request.email(), request.password(), http.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Canjea el refresh token de la cookie por un access token nuevo (rotación)",
            description = "El refresh viaja en la cookie HttpOnly y es de un solo uso: la respuesta instala uno "
                    + "nuevo. Si uno ya canjeado se vuelve a presentar, se revoca toda la sesión. Exige el header "
                    + "X-Requested-With: SmartElevate (defensa CSRF, ADR 0023); sin él, o con un Origin no "
                    + "permitido, responde 403.")
    @ApiResponse(responseCode = "200", description = "Access token nuevo; Set-Cookie con el refresh rotado",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "401",
            description = "Sin cookie, o refresh token inválido, vencido, revocado o ya usado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Falta el header CSRF o el Origin no está permitido",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest http, HttpServletResponse response) {
        refreshCookie.requireSameSiteRequest(http);
        try {
            String token = refreshCookie.read(http);
            if (token == null) {
                throw new InvalidRefreshTokenException();
            }
            return withCookie(authService.refresh(token));
        } catch (InvalidRefreshTokenException e) {
            // La cookie ya no sirve: se la borra del navegador junto con el 401 (arma el body el handler).
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.expire());
            throw e;
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Cierra la sesión: revoca el refresh token de la cookie y la expira",
            description = "Idempotente: sin cookie o con un token desconocido tampoco da error. Exige el header "
                    + "X-Requested-With: SmartElevate (ADR 0023).")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada; Set-Cookie expira la cookie")
    @ApiResponse(responseCode = "403", description = "Falta el header CSRF o el Origin no está permitido",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        refreshCookie.requireSameSiteRequest(http);
        String token = refreshCookie.read(http);
        if (token != null) {
            authService.logout(token);
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookie.expire()).build();
    }

    private ResponseEntity<TokenResponse> withCookie(SessionTokens session) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie.issue(session.refreshToken()))
                .body(session.response());
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

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Pide el link para restablecer la contraseña",
            description = "Responde 202 exista o no la cuenta (y haya o no un pedido reciente), para no revelar "
                    + "qué emails están registrados. Un pedido nuevo invalida el link anterior.")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordReset.request(request.email());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elige una contraseña nueva con el token del link",
            description = "El token sirve una sola vez y vence. Cambia la contraseña y revoca todos los refresh "
                    + "tokens del usuario (se cierran todas sus sesiones).")
    @ApiResponse(responseCode = "400", description = "Token inválido, usado o vencido, o contraseña fuera de política",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordReset.reset(request.token(), request.newPassword());
    }
}
