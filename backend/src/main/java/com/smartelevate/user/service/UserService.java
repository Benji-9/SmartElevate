package com.smartelevate.user.service;

import com.smartelevate.user.dto.RegisterRequest;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Cuentas de usuario. Por ahora, solo el registro. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

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

        User user = new User(request.fullName().trim(), email, legajo, passwordEncoder.encode(request.password()),
                request.declaredUserType(), clock.instant());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Otro pedido se registró entre el chequeo y el INSERT: el UNIQUE de la base es la defensa real.
            failIfTaken(email, legajo);
            throw e;
        }
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
