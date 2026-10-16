package com.smartelevate.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cookie del refresh token (ADR 0019): nombre y atributos técnicos, con override por variable de entorno. */
@ConfigurationProperties(prefix = "app.security.refresh-cookie")
public record RefreshCookieProperties(String name, boolean secure, String sameSite, String path) {

    public RefreshCookieProperties {
        name = name == null || name.isBlank() ? "refresh_token" : name;
        sameSite = sameSite == null || sameSite.isBlank() ? "Lax" : sameSite;
        path = path == null || path.isBlank() ? "/api/auth" : path;
    }
}
