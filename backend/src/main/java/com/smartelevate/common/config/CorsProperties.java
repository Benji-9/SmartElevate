package com.smartelevate.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Orígenes permitidos para CORS. Se configura con la variable de entorno
 * {@code CORS_ALLOWED_ORIGINS} (lista separada por comas).
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
