package com.smartelevate.priority.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Parámetros técnicos del buscador de usuarios del panel admin (no son reglas de negocio). */
@ConfigurationProperties("app.admin.user-search")
public record UserSearchProperties(int minQueryLength, int pageSize) {
}
