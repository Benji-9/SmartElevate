package com.smartelevate.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sesión JWT (ADR 0009). El secreto de firma viene de la variable de entorno {@code JWT_SECRET} (HS256, al menos
 * 32 bytes); si falta, en {@code dev} y tests se genera uno efímero y en {@code prod} la app no arranca
 * ({@link com.smartelevate.common.config.RequiredProdProperties}).
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl) {

    public JwtProperties {
        accessTtl = accessTtl == null ? Duration.ofMinutes(15) : accessTtl;
        refreshTtl = refreshTtl == null ? Duration.ofDays(7) : refreshTtl;
    }
}
