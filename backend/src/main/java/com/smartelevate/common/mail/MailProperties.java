package com.smartelevate.common.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del envío. {@code APP_MAIL_FROM}: remitente. {@code APP_MAIL_ENABLED}: {@code true} manda por SMTP
 * (host y credenciales en {@code SPRING_MAIL_*}); si no, solo se loguea (dev y tests).
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(@DefaultValue("no-reply@localhost") String from, @DefaultValue("false") boolean enabled) {
}
