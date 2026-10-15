package com.smartelevate.priority.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Parámetros técnicos del buscador de usuarios del panel admin (no son reglas de negocio). Se validan al
 * arrancar: el mínimo no puede ser menor a 2 (evita listar a todos) y la página, entre 1 y 100.
 */
@Validated
@ConfigurationProperties("app.admin.user-search")
public record UserSearchProperties(
        @DefaultValue("3") @Min(2) int minQueryLength,
        @DefaultValue("20") @Min(1) @Max(100) int pageSize) {
}
