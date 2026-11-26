package com.smartelevate.user.service;

import com.smartelevate.user.model.LoginFailure;
import com.smartelevate.user.repository.LoginFailureRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tope de pedidos por IP en una ventana deslizante para las acciones sin sesión que mandan mails o crean cuentas
 * (registro, reenvío de verificación, "olvidé mi contraseña"; #344). Cada acción cuenta por separado. Al llegar al
 * tope responde 429 hasta que el pedido más viejo sale de la ventana; los pedidos rechazados no se cuentan.
 *
 * <p>Reusa la tabla de {@link LoginThrottle} con un subject propio (hash de la acción y la IP, nunca la IP). Como
 * {@link LoginThrottle#recordFailure} poda con su ventana, la de acá no puede ser mayor: la app no arranca si lo es.
 */
@Service
public class RequestThrottle {

    private final LoginFailureRepository requests;
    private final RequestThrottleProperties props;
    private final Duration retention;
    private final Clock clock;

    public RequestThrottle(LoginFailureRepository requests, RequestThrottleProperties props,
                           LoginThrottleProperties loginProps, Clock clock) {
        if (props.window().compareTo(loginProps.window()) > 0) {
            throw new IllegalStateException(
                    "app.request-throttle.window no puede ser mayor que app.login-throttle.window");
        }
        this.requests = requests;
        this.props = props;
        this.retention = loginProps.window();
        this.clock = clock;
    }

    /**
     * Registra el pedido.
     *
     * @throws TooManyRequestsException si la IP ya llegó al tope de esta acción en la ventana
     */
    // ponytail: contar y después insertar no es atómico (igual que LoginThrottle); pedidos simultáneos pueden pasar
    // el tope por uno o dos. Alcanza para frenar abuso.
    @Transactional
    public void check(String action, String ip) {
        Instant now = clock.instant();
        Instant since = now.minus(props.window());
        String subject = AuthService.hash("req:" + action + ":" + ip);
        if (requests.countBySubjectAndFailedAtAfter(subject, since) >= props.maxPerIp()) {
            Duration retryAfter = requests.findFirstBySubjectAndFailedAtAfterOrderByFailedAtAsc(subject, since)
                    .map(oldest -> Duration.between(since, oldest.getFailedAt()))
                    .orElse(props.window());
            throw new TooManyRequestsException(retryAfter);
        }
        // Poda con la ventana más larga de la tabla (la de logins) para no borrar fallos de login que aún cuentan.
        requests.deleteOlderThan(now.minus(retention));
        requests.save(new LoginFailure(subject, now));
    }
}
