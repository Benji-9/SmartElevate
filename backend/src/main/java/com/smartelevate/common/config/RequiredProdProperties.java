package com.smartelevate.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;

/**
 * Variables obligatorias en {@code prod} (#222): si falta alguna o está en blanco, la app no arranca. En dev
 * esta clase no se registra y valen los defaults de {@code application-dev.yml}.
 *
 * <p>Una sola mecánica para todas: {@code @Validated} + constraints, activa solo con {@code @Profile("prod")}.
 * Para sumar una obligatoria, agregá su campo acá (o una clase igual en el feature) y dejá su default solo en
 * dev. Las clases de cada feature ({@code JwtProperties}, {@code CorsProperties}, ...) siguen leyendo el valor.
 */
@Profile("prod")
@Validated
@ConfigurationProperties(prefix = "app")
public record RequiredProdProperties(
        @Valid @NotNull Verification verification,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Security security) {

    /** {@code APP_FRONTEND_URL}: base de los links de los mails. */
    public record Verification(@NotBlank String frontendUrl) {
    }

    /** {@code CORS_ALLOWED_ORIGINS}: al menos un origen. */
    public record Cors(@NotEmpty List<@NotBlank String> allowedOrigins) {
    }

    public record Security(@Valid @NotNull Jwt jwt) {

        /** {@code JWT_SECRET}: el largo mínimo (32 bytes) lo controla {@code JwtConfig} en todos los perfiles. */
        public record Jwt(@NotBlank String secret) {
        }
    }
}
