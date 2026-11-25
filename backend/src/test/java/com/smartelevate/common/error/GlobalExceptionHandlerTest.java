package com.smartelevate.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

/** Errores que no son del dominio: cada uno responde con su status y el {@link ApiError} de siempre (#345). */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(Clock.fixed(Instant.parse("2026-11-25T15:00:00Z"), ZoneOffset.UTC));
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");

    @Test
    void unsupportedContentTypeIsA415NotA500() {
        ResponseEntity<ApiError> response = handler.handleServletException(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(415);
        assertThat(response.getBody().status()).isEqualTo(415);
        assertThat(response.getBody().path()).isEqualTo("/api/auth/login");
        assertThat(response.getBody().message()).contains("JSON");
    }

    @Test
    void anUnacceptableFormatIsA406() {
        ResponseEntity<ApiError> response = handler.handleServletException(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)), request);

        assertThat(response.getStatusCode().value()).isEqualTo(406);
        assertThat(response.getBody().message()).contains("application/json");
    }

    @Test
    void aServletErrorWithoutAClientStatusStaysA500() {
        ResponseEntity<ApiError> response = handler.handleServletException(new ServletException("falla interna"),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("Error interno del servidor");
    }

    @Test
    void aDataIntegrityConflictIsA409AndDoesNotLeakTheDatabaseMessage() {
        ResponseEntity<ApiError> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("Unique index violation: Key (email)=(ana@uade.edu.ar)"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().message()).doesNotContain("email").doesNotContain("ana@uade.edu.ar");
    }
}
