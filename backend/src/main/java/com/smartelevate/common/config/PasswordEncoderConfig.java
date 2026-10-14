package com.smartelevate.common.config;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Hasher de contraseñas (ADR 0015): Argon2id con los parámetros mínimos de OWASP (19 MiB, 2 pasadas, 1 hilo).
 * Los hashes llevan prefijo ({@code {argon2}}, {@code {bcrypt}}): los BCrypt de antes de #211 siguen validando
 * y el login los re-hashea con Argon2id ({@link PasswordEncoder#upgradeEncoding}).
 */
@Configuration
public class PasswordEncoderConfig {

    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 19 * 1024;
    private static final int ITERATIONS = 2;

    @Bean
    public PasswordEncoder passwordEncoder() {
        PasswordEncoder argon2 = new Argon2PasswordEncoder(SALT_BYTES, HASH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS);
        return new DelegatingPasswordEncoder("argon2", Map.of("argon2", argon2, "bcrypt", new BCryptPasswordEncoder()));
    }
}
