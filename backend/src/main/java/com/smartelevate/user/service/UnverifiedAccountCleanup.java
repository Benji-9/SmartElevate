package com.smartelevate.user.service;

import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Borra las cuentas que nunca verificaron el email pasado {@code app.unverified-cleanup.ttl} (#344). Sin esto, quien
 * se registra con un email o un legajo ajeno los retiene para siempre, y a la persona dueña le da 409 al registrarse.
 * Una cuenta sin verificar no puede iniciar sesión ni reservar, así que no pierde nada. Corre cada
 * {@code app.unverified-cleanup.interval} y se apaga con el resto de los jobs ({@code APP_SCHEDULING_ENABLED=false}).
 * La primera pasada espera un intervalo entero: un deploy no borra nada de inmediato.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnverifiedAccountCleanup {

    private final UserRepository users;
    private final UnverifiedCleanupProperties properties;
    private final Clock clock;

    /** Devuelve cuántas cuentas borró. */
    @Scheduled(fixedDelayString = "${app.unverified-cleanup.interval}",
            initialDelayString = "${app.unverified-cleanup.interval}")
    public int deleteStale() {
        int deleted = users.deleteStaleUnverified(clock.instant().minus(properties.ttl()));
        if (deleted > 0) {
            log.info("Se borraron {} cuentas que nunca verificaron el email", deleted);
        }
        return deleted;
    }
}
