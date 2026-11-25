package com.smartelevate.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;

/** El access token deja de valer al vencer: sin el margen de 60 s que Nimbus trae por defecto (#345). */
class JwtConfigTest {

    private final JwtConfig config = new JwtConfig();
    private final SecretKey key = config.jwtSecretKey(
            new JwtProperties("una-clave-de-prueba-de-al-menos-32-bytes!!", null, null));
    private final JwtEncoder encoder = config.jwtEncoder(key);
    private final JwtDecoder decoder = config.jwtDecoder(key);

    private String token(Instant issuedAt, Instant expiresAt) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject("u-1").issuedAt(issuedAt).expiresAt(expiresAt).build()))
                .getTokenValue();
    }

    @Test
    void aTokenThatHasNotExpiredIsAccepted() {
        Instant now = Instant.now();

        assertThat(decoder.decode(token(now, now.plusSeconds(900))).getSubject()).isEqualTo("u-1");
    }

    @Test
    void aTokenThatExpiredTenSecondsAgoIsRejected() {
        Instant now = Instant.now();
        // Con el margen por defecto de Nimbus (60 s) este token todavía se aceptaba.
        String expired = token(now.minusSeconds(100), now.minusSeconds(10));

        assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtValidationException.class);
    }
}
