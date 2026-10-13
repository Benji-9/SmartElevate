package com.smartelevate.user.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Reset de contraseña (#138). El link usa la misma base de front que la verificación ({@code APP_FRONTEND_URL}). */
@ConfigurationProperties(prefix = "app.password-reset")
public record PasswordResetProperties(
        @DefaultValue("PT30M") Duration tokenTtl,
        @DefaultValue("PT1M") Duration requestCooldown) {
}
