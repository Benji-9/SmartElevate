package com.smartelevate.user.service;

import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Borra las cuentas que siguen sin verificar pasado {@code app.verification.unverified-ttl} (#344): así un email o
 * un legajo registrado por otra persona no queda tomado para siempre. Nunca borra verificadas ni ADMIN.
 *
 * <p>De a una, cada una en su transacción: si una falla (p. ej. por una FK de otra tabla), se loguea y sigue con las
 * demás. Sus tokens de email y de refresh se borran en cascada (V8, V9).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnverifiedAccountCleanup {

    private final UserRepository users;
    private final VerificationProperties properties;
    private final Clock clock;

    /** Una pasada por hora alcanza para un TTL de días. Devuelve cuántas borró. */
    @Scheduled(cron = "@hourly")
    public int deleteExpired() {
        Instant cutoff = clock.instant().minus(properties.unverifiedTtl());
        int deleted = 0;
        for (UUID id : users.findUnverifiedIdsCreatedBefore(cutoff)) {
            try {
                // Repite las condiciones: si se verificó entre la consulta y el borrado, no se toca.
                deleted += users.deleteUnverifiedCreatedBefore(id, cutoff);
            } catch (RuntimeException e) {
                // Solo el tipo: el mensaje de la base puede traer datos de la fila.
                log.warn("No se pudo borrar la cuenta sin verificar {}: {}", id, e.getClass().getName());
            }
        }
        return deleted;
    }
}
