package com.smartelevate.user.service;

import com.smartelevate.user.model.EmailToken;
import com.smartelevate.user.model.TokenPurpose;
import com.smartelevate.user.repository.EmailTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tokens de un solo uso para links por mail. Sirve a la verificación de email (#119) y al reset de
 * contraseña (#138): cambia solo el {@link TokenPurpose}. Solo se guarda el hash.
 */
@Service
@RequiredArgsConstructor
public class EmailTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailTokenRepository tokens;
    private final Clock clock;

    /** Crea un token nuevo e invalida los pendientes del mismo usuario y propósito. Devuelve el token en claro. */
    @Transactional
    public String issue(UUID userId, TokenPurpose purpose, Duration ttl) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        tokens.deleteByUserIdAndPurposeAndUsedAtIsNull(userId, purpose);
        tokens.save(new EmailToken(userId, purpose, hash(raw), now, now.plus(ttl)));
        return raw;
    }

    /**
     * Gasta el token y devuelve el id del usuario. Si el llamador tiene una transacción, se suma a ella: si lo
     * que viene después falla, el token no queda gastado.
     *
     * @throws InvalidTokenException si no existe, ya se usó o venció
     */
    @Transactional
    public UUID consume(String raw, TokenPurpose purpose) {
        String hash = hash(raw);
        int updated = tokens.markUsed(hash, purpose, clock.instant());
        EmailToken token = tokens.findByTokenHash(hash)
                .filter(t -> t.getPurpose() == purpose)
                .orElseThrow(() -> new InvalidTokenException(InvalidTokenException.Reason.INVALID));
        if (updated == 1) {
            return token.getUserId();
        }
        throw new InvalidTokenException(token.getUsedAt() != null
                ? InvalidTokenException.Reason.USED : InvalidTokenException.Reason.EXPIRED);
    }

    public Optional<Instant> lastIssuedAt(UUID userId, TokenPurpose purpose) {
        return tokens.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose).map(EmailToken::getCreatedAt);
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
