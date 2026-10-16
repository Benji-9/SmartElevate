package com.smartelevate.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
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
        @Valid @NotNull Security security,
        @Valid @NotNull Kiosk kiosk) {

    private static final int MIN_DISTINCT_CHARS = 16;
    private static final Pattern ONLY_LOWERCASE = Pattern.compile("[a-z]+");
    private static final Pattern PLACEHOLDER = Pattern.compile("<.*>");
    /** Textos de la documentación o de ejemplos comunes que nunca pueden ser un secreto real. */
    private static final List<String> KNOWN_PLACEHOLDERS =
            List.of("openssl", "rand -base64", "change-me", "changeme", "change_me", "secret");

    private static final String HOW_TO = " Generalo con `openssl rand -base64 48` y cargalo en el dashboard; "
            + "no copies el comando ni un ejemplo.";

    @AssertTrue(message = "JWT_SECRET es un valor de ejemplo o de baja entropía (texto del comando, placeholder, "
            + "un solo carácter repetido o menos de 16 caracteres distintos)." + HOW_TO)
    public boolean isJwtSecretStrong() {
        return security == null || security.jwt() == null || isStrong(security.jwt().secret());
    }

    @AssertTrue(message = "APP_KIOSK_QR_SECRET es un valor de ejemplo o de baja entropía (texto del comando, "
            + "placeholder, un solo carácter repetido o menos de 16 caracteres distintos)." + HOW_TO)
    public boolean isKioskQrSecretStrong() {
        return kiosk == null || isStrong(kiosk.qrSecret());
    }

    @AssertTrue(message = "JWT_SECRET y APP_KIOSK_QR_SECRET no pueden ser iguales: generá una clave distinta para "
            + "cada una con `openssl rand -base64 48`.")
    public boolean isSecretsDistinct() {
        if (security == null || security.jwt() == null || kiosk == null) {
            return true;
        }
        String jwt = security.jwt().secret();
        return jwt == null || jwt.isBlank() || !jwt.strip().equals(kiosk.qrSecret() == null ? null : kiosk.qrSecret().strip());
    }

    /** Blanco se deja a {@code @NotBlank}; el largo mínimo (32 bytes) lo controlan JwtConfig y QrTokenService. */
    private static boolean isStrong(String secret) {
        if (secret == null || secret.isBlank()) {
            return true;
        }
        String s = secret.strip();
        String lower = s.toLowerCase(Locale.ROOT);
        return KNOWN_PLACEHOLDERS.stream().noneMatch(lower::contains)
                && !PLACEHOLDER.matcher(s).matches()
                && !ONLY_LOWERCASE.matcher(s).matches()
                && s.chars().distinct().count() >= MIN_DISTINCT_CHARS;
    }

    /**
     * {@code APP_KIOSK_QR_SECRET}: clave HMAC del QR (#229). El largo mínimo (32 bytes) lo controla
     * {@code QrTokenService} en todos los perfiles.
     */
    public record Kiosk(@NotBlank String qrSecret) {
    }

    /** {@code APP_FRONTEND_URL}: base de los links de los mails. */
    public record Verification(@NotBlank String frontendUrl) {
    }

    /** {@code CORS_ALLOWED_ORIGINS}: al menos un origen. */
    public record Cors(@NotEmpty List<@NotBlank String> allowedOrigins) {
    }

    public record Security(@Valid @NotNull Jwt jwt, @Valid @NotNull RefreshCookie refreshCookie) {

        /**
         * {@code APP_REFRESH_COOKIE_SECURE}: en prod no puede ser {@code false} (la cookie del refresh viajaría
         * por HTTP). El default de {@code application.yml} es {@code true}; {@code false} es solo para dev.
         */
        public record RefreshCookie(
                @AssertTrue(message = "APP_REFRESH_COOKIE_SECURE no puede ser false en producción: la cookie del "
                        + "refresh token tiene que ser Secure. Quitá la variable (el default es true); false es "
                        + "solo para desarrollo local por http.") boolean secure) {
        }

        /** {@code JWT_SECRET}: el largo mínimo (32 bytes) lo controla {@code JwtConfig} en todos los perfiles. */
        public record Jwt(@NotBlank String secret) {
        }
    }
}
