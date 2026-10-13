package com.smartelevate.common.security;

import com.smartelevate.user.model.Role;
import java.util.UUID;

/**
 * Usuario autenticado de la request. Se obtiene en controllers con {@code @AuthenticationPrincipal CurrentUser user},
 * sin depender de detalles del JWT. El rol sale de la base en cada request, no del token.
 */
public record CurrentUser(UUID id, Role role) {
}
