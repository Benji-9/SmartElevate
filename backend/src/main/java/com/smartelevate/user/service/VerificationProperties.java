package com.smartelevate.user.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Verificación de email. {@code frontendUrl} (env {@code APP_FRONTEND_URL}) es la base del link del mail:
 * la pantalla {@code /verificar-email?token=...} llama a {@code POST /api/auth/verify}.
 */
@ConfigurationProperties(prefix = "app.verification")
public record VerificationProperties(
        @DefaultValue("PT24H") Duration tokenTtl,
        @DefaultValue("PT1M") Duration resendCooldown,
        @DefaultValue("http://localhost:5173") String frontendUrl) {
}
