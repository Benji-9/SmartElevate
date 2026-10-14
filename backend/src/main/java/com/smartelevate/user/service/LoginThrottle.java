package com.smartelevate.user.service;

import com.smartelevate.user.model.LoginFailure;
import com.smartelevate.user.repository.LoginFailureRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Límite de logins fallidos (ADR 0015), por cuenta y por IP, en una ventana deslizante
 * ({@link LoginThrottleProperties}, en {@code application.yml}).
 * Con el tope alcanzado se responde 429 sin mirar la contraseña, hasta que el fallo más viejo sale de la ventana:
 * el bloqueo siempre es temporal, así que un tercero no puede dejar una cuenta bloqueada para siempre, y el
 * tope por cuenta no pasa de 100 (NIST SP 800-63B). Un login correcto o un reset de contraseña borran los fallos
 * de la cuenta; los de la IP no, para que acertar con una cuenta propia no habilite seguir probando otras.
 *
 * <p>La cuenta se identifica por el email normalizado, exista o no: así el 429 no revela qué emails tienen cuenta.
 * Se guardan hashes, nunca el email ni la IP.
 */
@Service
@RequiredArgsConstructor
public class LoginThrottle {

    private final LoginFailureRepository failures;
    private final LoginThrottleProperties props;
    private final Clock clock;

    /** @throws TooManyLoginAttemptsException si la cuenta o la IP llegaron al tope de fallos de la ventana */
    public void check(String account, String ip) {
        Instant now = clock.instant();
        Instant since = now.minus(props.window());
        Stream.of(wait(accountKey(account), props.maxFailuresPerAccount(), since, now),
                        wait(ipKey(ip), props.maxFailuresPerIp(), since, now))
                .flatMap(Optional::stream)
                .max(Duration::compareTo)
                .ifPresent(retryAfter -> {
                    throw new TooManyLoginAttemptsException(retryAfter);
                });
    }

    // ponytail: contar y después insertar no es atómico; dos fallos simultáneos pueden pasar el tope por uno o dos.
    // Alcanza para frenar fuerza bruta; si hiciera falta exactitud, un contador con UPDATE condicional.
    @Transactional
    public void recordFailure(String account, String ip) {
        Instant now = clock.instant();
        failures.deleteOlderThan(now.minus(props.window()));
        failures.save(new LoginFailure(accountKey(account), now));
        failures.save(new LoginFailure(ipKey(ip), now));
    }

    /** Olvida los fallos de la cuenta (login correcto o contraseña restablecida). */
    public void clearAccount(String account) {
        failures.deleteBySubject(accountKey(account));
    }

    /** Lo que falta para que el fallo más viejo salga de la ventana, si ya se llegó al tope. */
    private Optional<Duration> wait(String subject, int max, Instant since, Instant now) {
        if (failures.countBySubjectAndFailedAtAfter(subject, since) < max) {
            return Optional.empty();
        }
        return failures.findFirstBySubjectAndFailedAtAfterOrderByFailedAtAsc(subject, since)
                .map(oldest -> Duration.between(since, oldest.getFailedAt()));
    }

    private static String accountKey(String account) {
        return AuthService.hash("account:" + account);
    }

    private static String ipKey(String ip) {
        return AuthService.hash("ip:" + ip);
    }
}
