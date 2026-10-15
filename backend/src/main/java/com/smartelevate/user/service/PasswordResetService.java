package com.smartelevate.user.service;

import com.smartelevate.common.mail.EmailSender;
import com.smartelevate.common.validation.PasswordPolicy;
import com.smartelevate.user.model.TokenPurpose;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.RefreshTokenRepository;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Olvidé mi contraseña (#138). Nada de lo que devuelve o lanza {@link #request} depende de si la cuenta existe. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final EmailTokenService tokens;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final EmailVerificationService emailVerification;
    private final PasswordResetProperties properties;
    private final VerificationProperties verificationProperties;
    private final Clock clock;
    private final LoginThrottle loginThrottle;

    /**
     * Manda el link de reset. Si el email no existe, o ya se pidió hace menos de la espera mínima, no hace nada:
     * un 429 solo para cuentas reales revelaría cuáles existen. Una cuenta sin verificar recibe de nuevo el link
     * de verificación (con su propia espera). Los fallos de envío se loguean y no se propagan.
     */
    public void request(String email) {
        User user = users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (user == null) {
            return;
        }
        try {
            if (!user.isEmailVerified()) {
                emailVerification.resend(user.getEmail());
                return;
            }
            Instant now = clock.instant();
            boolean tooSoon = tokens.lastIssuedAt(user.getId(), TokenPurpose.PASSWORD_RESET)
                    .filter(last -> Duration.between(last, now).compareTo(properties.requestCooldown()) < 0)
                    .isPresent();
            if (!tooSoon) {
                send(user);
            }
        } catch (ResendTooSoonException e) {
            // Igual que arriba: se calla.
        } catch (RuntimeException e) {
            log.error("No se pudo mandar el mail de reset al usuario {}", user.getId(), e);
        }
    }

    private void send(User user) {
        String token = tokens.issue(user.getId(), TokenPurpose.PASSWORD_RESET, properties.tokenTtl());
        String link = verificationProperties.frontendUrl() + "/recuperar/nueva?token=" + token;
        emailSender.send(user.getEmail(), "Restablecé tu contraseña de SmartElevate",
                "Hola " + user.getFullName() + ",\n\nElegí una contraseña nueva con este link (vale "
                        + properties.tokenTtl().toMinutes() + " minutos y se usa una sola vez):\n" + link
                        + "\n\nSi no lo pediste vos, ignorá este mail: tu contraseña sigue igual.");
    }

    /**
     * Gasta el token, guarda la contraseña nueva y cierra todas las sesiones del usuario, todo o nada.
     *
     * @throws InvalidTokenException si el token no existe, ya se usó o venció
     */
    @Transactional
    public void reset(String token, String newPassword) {
        User user = users.findById(tokens.consume(token, TokenPurpose.PASSWORD_RESET))
                .orElseThrow(() -> new InvalidTokenException(InvalidTokenException.Reason.INVALID));
        user.changePasswordHash(passwordEncoder.encode(PasswordPolicy.normalize(newPassword)));
        // Quien recupera la cuenta por mail no tiene que esperar a que venza el límite de intentos.
        loginThrottle.clearAccount(user.getEmail());
        refreshTokens.revokeAllByUser(user.getId(), clock.instant());
    }
}
