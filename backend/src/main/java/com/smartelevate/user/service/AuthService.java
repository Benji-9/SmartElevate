package com.smartelevate.user.service;

import com.smartelevate.common.security.JwtProperties;
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
    private final SecureRandom random = new SecureRandom();
    /** Hash de una clave cualquiera: se compara cuando el email no existe para que tarde lo mismo. */
    private volatile String dummyHash;

    public TokenResponse login(String email, String password) {
        User user = users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElse(null);
        boolean matches = passwordEncoder.matches(password, user != null ? user.getPasswordHash() : dummyHash());
        if (user == null || !matches) {
            throw new InvalidCredentialsException();
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

    public TokenResponse refresh(String rawRefreshToken) {
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

    private TokenResponse issue(User user, UUID familyId) {
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
        return new TokenResponse(access, refresh, TOKEN_TYPE, props.accessTtl().toSeconds());
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 existe en toda JVM
        }
    }
}
