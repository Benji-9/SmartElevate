package com.smartelevate.priority.service;

import com.smartelevate.priority.dto.AdminUserPage;
import com.smartelevate.priority.dto.AdminUserResponse;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Buscador de usuarios del panel admin, para elegir a quién cambiarle el rol (#215). */
@Service
@RequiredArgsConstructor
public class UserSearchService {

    /** Tope técnico: un email o nombre real no lo pasa; evita patrones LIKE enormes. */
    static final int MAX_QUERY_LENGTH = 100;
    private static final UUID START = new UUID(0, 0);

    private final UserRepository users;
    private final UserSearchProperties props;

    // ponytail: minúsculas sí, acentos no ("Jose" no encuentra "José"); agregar unaccent/normalizado si se pide.
    @Transactional(readOnly = true)
    public AdminUserPage search(String query, UUID cursor) {
        String q = query == null ? "" : query.strip();
        if (q.length() < props.minQueryLength()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La búsqueda necesita al menos " + props.minQueryLength() + " caracteres");
        }
        if (q.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La búsqueda admite como máximo " + MAX_QUERY_LENGTH + " caracteres");
        }
        String pattern = "%" + q.toLowerCase(Locale.ROOT).replaceAll("[!%_]", "!$0") + "%";
        // Se pide uno de más para saber si hay página siguiente.
        List<User> found = users.search(pattern, cursor == null ? START : cursor,
                PageRequest.of(0, props.pageSize() + 1));
        boolean more = found.size() > props.pageSize();
        List<User> page = more ? found.subList(0, props.pageSize()) : found;
        return new AdminUserPage(
                page.stream().map(u -> new AdminUserResponse(u.getId(), u.getFullName(), u.getEmail(),
                        u.getLegajo(), u.getRole(), u.getDeclaredUserType(), u.isEmailVerified())).toList(),
                more ? page.get(page.size() - 1).getId() : null);
    }
}
