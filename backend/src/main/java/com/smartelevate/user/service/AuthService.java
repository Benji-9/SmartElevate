package com.smartelevate.user.service;

import com.smartelevate.common.security.JwtProperties;
import com.smartelevate.common.validation.PasswordPolicy;
import com.smartelevate.user.dto.SessionTokens;
import com.smartelevate.user.dto.TokenResponse;
import com.smartelevate.user.model.RefreshToken;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.RefreshTokenRepository;
import com.smartelevate.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Sesión: login, refresh con rotación y logout (ADR 0009).
 *
 * <p>El access token es un JWT con solo {@code sub} (id del usuario), {@code iat} y {@code exp}: ni rol ni
 * prioridad. El refresh es un valor aleatorio opaco que se guarda hasheado. No es {@code @Transactional}: el
 * canje y la revocación de familia tienen que quedar confirmados aunque después se lance el error 401.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties props;
    private final Clock clock;
    private final LoginThrottle loginThrottle;
    private final SecureRandom random = new SecureRandom();
    /** Hash Argon2id de una clave cualquiera: se compara cuando el email no existe para que tarde lo mismo. */
    private volatile String dummyHash;

    /**
     * @param ip dirección del cliente, para el límite de intentos por IP
     * @throws TooManyLoginAttemptsException si la cuenta o la IP agotaron los intentos (no se mira la clave)
     */
    public SessionTokens login(String email, String password, String ip) {
        String account = email.trim().toLowerCase(Locale.ROOT);
        loginThrottle.check(account, ip);
        // La misma normalización que al hashear en el registro y el reset (ADR 0015).
        String normalized = PasswordPolicy.normalize(password);
        User user = users.findByEmail(account).orElse(null);
        boolean matches = passwordEncoder.matches(normalized, user != null ? user.getPasswordHash() : dummyHash());
        if (user == null || !matches) {
            loginThrottle.recordFailure(account, ip);
            throw new InvalidCredentialsException();
        }
        loginThrottle.clearAccount(account);
        // Hash viejo (BCrypt) o con parámetros desactualizados: se aprovecha que tenemos la clave para re-hashearla.
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.changePasswordHash(passwordEncoder.encode(normalized));
            users.save(user);
        }
        // Recién con la clave correcta se avisa que falta verificar: antes revelaría que el email existe.
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
        return issue(user, UUID.randomUUID());
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("sin-cuenta");
        }
        return dummyHash;
    }

    public SessionTokens refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (token.getUsedAt() != null || token.getRevokedAt() != null) {
            // Un token ya rotado que reaparece: puede estar robado, se cierra toda la sesión.
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }
        if (refreshTokens.markUsed(token.getId(), now) == 0) {
            // Otro pedido lo canjeó justo antes.
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }
        User user = users.findById(token.getUserId()).orElseThrow(InvalidRefreshTokenException::new);
        return issue(user, token.getFamilyId());
    }

    /** Revoca la sesión del refresh token. Idempotente: un token desconocido no es error. */
    public void logout(String rawRefreshToken) {
        refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), clock.instant()));
    }

    private SessionTokens issue(User user, UUID familyId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTtl()))
                .build();
        String access = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user.getId(), familyId, hash(refresh), now, now.plus(props.refreshTtl())));
        return new SessionTokens(new TokenResponse(access, TOKEN_TYPE, props.accessTtl().toSeconds()), refresh);
    }

    /**
     * SHA-256 en hex. También lo usan {@link LoginThrottle}, para no guardar emails ni IPs, y los kioscos, para no
     * guardar su secreto.
     */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 existe en toda JVM
        }
    }
}
