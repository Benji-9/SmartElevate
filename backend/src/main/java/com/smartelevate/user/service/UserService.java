package com.smartelevate.user.service;

import com.smartelevate.common.validation.PasswordPolicy;
import com.smartelevate.priority.service.PriorityService;
import com.smartelevate.user.dto.RegisterRequest;
import com.smartelevate.user.dto.UserResponse;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Cuentas de usuario: registro y datos del usuario de la sesión. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final EmailVerificationService emailVerification;
    private final PriorityService priorityService;

    /**
     * Usuario de la sesión. El rol sale de la base y la prioridad de {@link PriorityService#currentPriority}, nunca
     * de un claim del JWT (ADR 0009). Si la cuenta se borró después de autenticar, se trata como sesión inválida.
     */
    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido"));
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getLegajo(), user.getRole(),
                priorityService.currentPriority(userId), user.getDeclaredUserType());
    }

    /**
     * Crea una cuenta común y sin verificar.
     *
     * <p>No es {@code @Transactional} a propósito: si el INSERT choca con un UNIQUE, Postgres da por abortada la
     * transacción y ya no deja consultar. Así cada llamada al repositorio tiene la suya y, tras el choque,
     * podemos averiguar qué campo fue.
     *
     * @throws DuplicateUserException si el email o el legajo ya tienen cuenta
     */
    public void register(RegisterRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        String legajo = request.legajo();
        failIfTaken(email, legajo);

        User user = new User(request.fullName().trim(), email, legajo,
                passwordEncoder.encode(PasswordPolicy.normalize(request.password())), request.declaredUserType(),
                clock.instant());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Otro pedido se registró entre el chequeo y el INSERT: el UNIQUE de la base es la defensa real.
            failIfTaken(email, legajo);
            throw e;
        }
        // Si el mail falla la cuenta queda creada y la persona pide el reenvío.
        emailVerification.sendVerificationQuietly(user);
    }

    private void failIfTaken(String email, String legajo) {
        List<String> taken = new ArrayList<>();
        if (users.existsByEmail(email)) {
            taken.add("email");
        }
        if (users.existsByLegajo(legajo)) {
            taken.add("legajo");
        }
        if (!taken.isEmpty()) {
            throw new DuplicateUserException(taken);
        }
    }
}
