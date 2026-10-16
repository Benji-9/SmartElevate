package com.smartelevate.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Firma y verificación de access tokens con la misma clave simétrica (HS256). */
@Slf4j
@Configuration
public class JwtConfig {

    static final int MIN_SECRET_BYTES = 32;

    @Bean
    SecretKey jwtSecretKey(JwtProperties props) {
        byte[] bytes;
        if (props.secret() == null || props.secret().isBlank()) {
            // En prod la app no arranca sin JWT_SECRET: lo valida RequiredProdProperties (#222).
            // ponytail: clave efímera en dev/tests; los tokens no sobreviven a un reinicio.
            log.warn("JWT_SECRET no está definido: se usa una clave efímera (solo dev/tests)");
            bytes = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(bytes);
        } else {
            bytes = props.secret().getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("JWT_SECRET es demasiado corto: mínimo 32 bytes");
            }
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
