package com.smartelevate.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.LoginFailureRepository;
import com.smartelevate.user.repository.RefreshTokenRepository;
import com.smartelevate.user.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** El login de punta a punta (#211): upgrade de BCrypt a Argon2id y límite de intentos con los valores de V13. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class LoginApiTest {

    private static final String EMAIL = "ana@uade.edu.ar";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private LoginFailureRepository failures;

    @BeforeEach
    @AfterEach
    void clean() {
        refreshTokens.deleteAll();
        failures.deleteAll();
        users.deleteAll();
    }

    /** Una cuenta de antes de #211: hash BCrypt al que V13 le agregó el prefijo. */
    private User legacyUser(String password) {
        User user = new User("Ana Pérez", EMAIL, "1111111", "{bcrypt}" + new BCryptPasswordEncoder(4).encode(password),
                DeclaredUserType.STUDENT, Instant.now());
        user.verifyEmail();
        return users.save(user);
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    @Test
    void aLegacyBcryptAccountLogsInAndIsRehashedWithArgon2id() throws Exception {
        legacyUser("una-clave-larga");

        login(EMAIL, "una-clave-larga").andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());

        String hash = users.findByEmail(EMAIL).orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("{argon2}$argon2id$");
        // Y con el hash nuevo se sigue entrando.
        login(EMAIL, "una-clave-larga").andExpect(status().isOk());
    }

    @Test
    void afterTenFailuresTheAccountGets429WithRetryAfterEvenWithTheRightPassword() throws Exception {
        legacyUser("una-clave-larga");
        for (int i = 0; i < 10; i++) {
            login(EMAIL, "otra-clave-larga").andExpect(status().isUnauthorized());
        }

        login(EMAIL, "una-clave-larga")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
        String retryAfter = login(EMAIL, "una-clave-larga").andReturn().getResponse().getHeader("Retry-After");
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 900L);
        // El hash no se tocó: no se llegó a comparar la clave.
        assertThat(users.findByEmail(EMAIL).orElseThrow().getPasswordHash()).startsWith("{bcrypt}");
    }

    @Test
    void anUnknownEmailIsLimitedTheSameWaySoThe429DoesNotRevealAccounts() throws Exception {
        for (int i = 0; i < 10; i++) {
            login("nadie@uade.edu.ar", "otra-clave-larga").andExpect(status().isUnauthorized());
        }

        login("nadie@uade.edu.ar", "otra-clave-larga").andExpect(status().isTooManyRequests());
    }

    @Test
    void aSuccessfulLoginForgetsThePreviousFailures() throws Exception {
        legacyUser("una-clave-larga");
        for (int i = 0; i < 9; i++) {
            login(EMAIL, "otra-clave-larga").andExpect(status().isUnauthorized());
        }
        login(EMAIL, "una-clave-larga").andExpect(status().isOk());

        for (int i = 0; i < 9; i++) {
            login(EMAIL, "otra-clave-larga").andExpect(status().isUnauthorized());
        }
        login(EMAIL, "una-clave-larga").andExpect(status().isOk());
    }
}
