package com.smartelevate.user.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Verificación de email. {@code frontendUrl} (env {@code APP_FRONTEND_URL}) es la base del link del mail:
 * la pantalla {@code /verificar-email?token=...} llama a {@code POST /api/auth/verify}. Las cuentas sin verificar
 * se borran pasado {@code unverifiedTtl} ({@link UnverifiedAccountCleanup}), que tiene que ser mayor que
 * {@code tokenTtl}: si no, se borraría una cuenta con el link todavía vigente.
 */
@ConfigurationProperties(prefix = "app.verification")
public record VerificationProperties(
        @DefaultValue("PT24H") Duration tokenTtl,
        @DefaultValue("PT1M") Duration resendCooldown,
        @DefaultValue("P2D") Duration unverifiedTtl,
        String frontendUrl) {

    public VerificationProperties {
        if (tokenTtl != null && unverifiedTtl != null && unverifiedTtl.compareTo(tokenTtl) <= 0) {
            throw new IllegalArgumentException(
                    "app.verification.unverified-ttl tiene que ser mayor que app.verification.token-ttl");
        }
    }
}
