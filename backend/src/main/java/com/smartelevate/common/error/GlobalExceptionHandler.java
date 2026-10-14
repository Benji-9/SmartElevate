package com.smartelevate.common.error;

import com.smartelevate.common.security.KioskAuthenticationException;
import com.smartelevate.elevator.service.KioskException;
import com.smartelevate.elevator.service.KioskRateLimitedException;
import com.smartelevate.turn.service.ReservationRejectedException;
import com.smartelevate.turn.service.TurnFullException;

import com.smartelevate.priority.service.InvalidPriorityOperationException;
import com.smartelevate.priority.service.PriorityNotFoundException;
import com.smartelevate.user.service.DuplicateUserException;
import com.smartelevate.user.service.EmailNotVerifiedException;
import com.smartelevate.user.service.InvalidCredentialsException;
import com.smartelevate.user.service.InvalidRefreshTokenException;
import com.smartelevate.user.service.InvalidTokenException;
import com.smartelevate.user.service.ResendTooSoonException;
import com.smartelevate.user.service.TooManyLoginAttemptsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Clock clock;

    // @ResponseStatus hace que springdoc documente estas respuestas (con ApiError) en todos los endpoints.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "La solicitud tiene campos inválidos", request, violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(cv -> new ApiError.FieldViolation(cv.getPropertyPath().toString(), cv.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "La solicitud tiene parámetros inválidos", request, violations);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        List<ApiError.FieldViolation> violations =
                List.of(new ApiError.FieldViolation(ex.getName(), "Tiene un valor inválido"));
        return build(HttpStatus.BAD_REQUEST, "La solicitud tiene parámetros inválidos", request, violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud es inválido", request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Recurso no encontrado", request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return build(status, message, request, List.of());
    }

    // Sin @ResponseStatus a propósito: este 409 solo lo da el registro y se documenta en ese endpoint,
    // no en todos como los de arriba.
    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<ApiError> handleDuplicateUser(DuplicateUserException ex, HttpServletRequest request) {
        // "email" y "legajo" son masculinos: "Ya hay una cuenta con ese email."
        List<ApiError.FieldViolation> violations = ex.getFields().stream()
                .map(field -> new ApiError.FieldViolation(field, "Ya hay una cuenta con ese " + field + "."))
                .toList();
        return build(HttpStatus.CONFLICT, "Ya existe una cuenta con esos datos", request, violations);
    }

    // Sin @ResponseStatus: se documentan en los endpoints de auth (401/403) y por la seguridad en el resto.
    @ExceptionHandler({InvalidCredentialsException.class, InvalidRefreshTokenException.class})
    public ResponseEntity<ApiError> handleBadSession(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ApiError> handleEmailNotVerified(EmailNotVerifiedException ex,
                                                           HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request, List.of());
    }

    /** Sin token, vencido o manipulado: lo lanza el filtro de seguridad (vía el entry point) o un @PreAuthorize. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleUnauthenticated(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Necesitás iniciar sesión", request, List.of());
    }

    // Sin @ResponseStatus: se documenta en GET /api/kiosks/me/qr-token.
    @ExceptionHandler(KioskAuthenticationException.class)
    public ResponseEntity<ApiError> handleKioskUnauthenticated(KioskAuthenticationException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), "KIOSK_UNAUTHORIZED", request, List.of());
    }

    @ExceptionHandler(KioskRateLimitedException.class)
    public ResponseEntity<ApiError> handleKioskRateLimited(KioskRateLimitedException ex,
                                                           HttpServletRequest request) {
        return tooManyRequests(ex.getMessage(), ex.getRetryAfterSeconds(), request);
    }

    @ExceptionHandler(KioskException.class)
    public ResponseEntity<ApiError> handleKiosk(KioskException ex, HttpServletRequest request) {
        return build(ex.getReason().getStatus(), ex.getMessage(), ex.getReason().name(), request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "No tenés permisos para esta acción", request, List.of());
    }

    @ExceptionHandler(PriorityNotFoundException.class)
    public ResponseEntity<ApiError> handlePriorityNotFound(PriorityNotFoundException ex,
                                                           HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(InvalidPriorityOperationException.class)
    public ResponseEntity<ApiError> handleInvalidPriorityOperation(InvalidPriorityOperationException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiError> handleInvalidToken(InvalidTokenException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ResendTooSoonException.class)
    public ResponseEntity<ApiError> handleResendTooSoon(ResendTooSoonException ex, HttpServletRequest request) {
        return tooManyRequests(ex.getMessage(), ex.getRetryAfter().toSeconds(), request);
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<ApiError> handleTooManyLoginAttempts(TooManyLoginAttemptsException ex,
                                                               HttpServletRequest request) {
        return tooManyRequests(ex.getMessage(), ex.getRetryAfterSeconds(), request);
    }

    private ResponseEntity<ApiError> tooManyRequests(String message, long retryAfterSeconds,
                                                     HttpServletRequest request) {
        ResponseEntity<ApiError> response = build(HttpStatus.TOO_MANY_REQUESTS, message, request, List.of());
        return ResponseEntity.status(response.getStatusCode())
                .header("Retry-After", String.valueOf(retryAfterSeconds))
                .body(response.getBody());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", request, List.of());
    }

    // Sin @ResponseStatus: se documentan en POST /api/reservations.
    @ExceptionHandler(TurnFullException.class)
    public ResponseEntity<ApiError> handleTurnFull(TurnFullException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), "TURN_FULL", request, List.of());
    }

    @ExceptionHandler(ReservationRejectedException.class)
    public ResponseEntity<ApiError> handleReservationRejected(ReservationRejectedException ex,
                                                              HttpServletRequest request) {
        return build(ex.getReason().getStatus(), ex.getMessage(), ex.getReason().name(), request, List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request,
                                           List<ApiError.FieldViolation> violations) {
        return build(status, message, null, request, violations);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, String code,
                                           HttpServletRequest request, List<ApiError.FieldViolation> violations) {
        ApiError body = new ApiError(clock.instant(), status.value(), status.getReasonPhrase(), message, code,
                request.getRequestURI(), violations);
        return ResponseEntity.status(status).body(body);
    }
}
