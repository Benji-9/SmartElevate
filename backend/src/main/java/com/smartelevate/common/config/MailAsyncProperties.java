package com.smartelevate.common.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Executor de los mails masivos ({@link AsyncConfig}). Parámetro técnico: {@code application.yml} con override por
 * variable de entorno. Se valida al arrancar: con valores fuera de rango la app no levanta.
 */
@Validated
@ConfigurationProperties(prefix = "app.mail.async")
public record MailAsyncProperties(
        @DefaultValue("2") @Min(1) int threads,
        @DefaultValue("50") @Min(0) int queue) {
}
