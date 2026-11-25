package com.smartelevate.user.service;

import com.smartelevate.common.mail.EmailSender;
import com.smartelevate.user.model.TokenPurpose;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Verificación del email institucional por link (ADR 0009). */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailTokenService tokens;
    private final UserRepository users;
    private final EmailSender emailSender;
    private final VerificationProperties properties;
    private final Clock clock;

    /** Genera un token nuevo y manda el link. Los anteriores sin usar quedan invalidados. */
    public void sendVerification(User user) {
        String token = tokens.issue(user.getId(), TokenPurpose.VERIFY_EMAIL, properties.tokenTtl());
        String link = properties.frontendUrl() + "/verificar-email?token=" + token;
        emailSender.send(user.getEmail(), "Verificá tu email en SmartElevate",
                "Hola " + user.getFullName() + ",\n\nConfirmá tu email con este link (vale "
                        + properties.tokenTtl().toHours() + " horas y se usa una sola vez):\n" + link
                        + "\n\nSi no creaste una cuenta, ignorá este mail.");
    }

    /** Como {@link #sendVerification} pero sin propagar fallos: el registro no debe caerse si el mail falla. */
    public void sendVerificationQuietly(User user) {
        try {
            sendVerification(user);
        } catch (RuntimeException e) {
            log.error("No se pudo mandar el mail de verificación al usuario {}", user.getId(), e);
        }
    }

    /** Gasta el token y marca la cuenta como verificada, todo o nada. */
    @Transactional
    public void verify(String token) {
        users.findById(tokens.consume(token, TokenPurpose.VERIFY_EMAIL))
                .orElseThrow(() -> new InvalidTokenException(InvalidTokenException.Reason.INVALID))
                .verifyEmail();
    }

    /**
     * Reenvía el link. Si el email no existe, ya está verificado o el último mail salió hace menos de la espera
     * mínima, no hace nada y no lo dice (#344): un 429 solo para las cuentas reales sin verificar revelaría cuáles
     * existen, igual que en {@link PasswordResetService#request}.
     */
    public void resend(String email) {
        User user = users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (user == null || user.isEmailVerified()) {
            return;
        }
        Instant now = clock.instant();
        boolean tooSoon = tokens.lastIssuedAt(user.getId(), TokenPurpose.VERIFY_EMAIL)
                .filter(last -> Duration.between(last, now).compareTo(properties.resendCooldown()) < 0)
                .isPresent();
        if (!tooSoon) {
            sendVerification(user);
        }
    }
}
