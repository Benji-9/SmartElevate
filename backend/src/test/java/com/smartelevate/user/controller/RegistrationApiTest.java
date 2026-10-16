package com.smartelevate.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.Role;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** El registro de punta a punta: HTTP, validación, servicio, Hibernate y la base real con las migraciones. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@ExtendWith(OutputCaptureExtension.class)
class RegistrationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoggingSystem loggingSystem;

    @BeforeEach
    @AfterEach
    void cleanUsers() {
        users.deleteAll();
    }

    private ResultActions register(String json) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String json(String email, String legajo) {
        return """
                {"fullName":"Ana Pérez","email":"%s","legajo":"%s","password":"una-clave-larga",
                 "declaredUserType":"STUDENT"}""".formatted(email, legajo);
    }

    @Test
    void aValidRegistrationCreatesACommonUnverifiedAccount() throws Exception {
        register(json("ana@uade.edu.ar", "1111111")).andExpect(status().isCreated());

        assertThat(users.count()).isEqualTo(1);
        User user = users.findAll().getFirst();
        assertThat(user.getId()).isNotNull();
        assertThat(user.getFullName()).isEqualTo("Ana Pérez");
        assertThat(user.getEmail()).isEqualTo("ana@uade.edu.ar");
        assertThat(user.getLegajo()).isEqualTo("1111111");
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getDeclaredUserType()).isEqualTo(DeclaredUserType.STUDENT);
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    void thePasswordIsStoredOnlyAsAnArgon2idHash() throws Exception {
        register(json("ana@uade.edu.ar", "1111111")).andExpect(status().isCreated());

        String stored = users.findAll().getFirst().getPasswordHash();
        // Parámetros OWASP: 19 MiB, 2 pasadas, 1 hilo. Entra en la columna ampliada por V13.
        assertThat(stored).startsWith("{argon2}$argon2id$v=19$m=19456,t=2,p=1$").hasSizeLessThanOrEqualTo(255)
                .doesNotContain("una-clave-larga");
        assertThat(passwordEncoder.matches("una-clave-larga", stored)).isTrue();
        assertThat(passwordEncoder.matches("otra-clave-larga", stored)).isFalse();
    }

    @Test
    void sixtyFourNonAsciiCharactersAreAcceptedAndHashedAfterNfkc() throws Exception {
        // 64 "ñ" descompuestas (n + U+0303): 128 code points antes de NFKC, 64 después. Con BCrypt no entraban.
        register("""
                {"fullName":"Ana Pérez","email":"ana@uade.edu.ar","legajo":"1111111","password":"%s",
                 "declaredUserType":"STUDENT"}""".formatted("ñ".repeat(64)))
                .andExpect(status().isCreated());

        assertThat(passwordEncoder.matches("ñ".repeat(64), users.findAll().getFirst().getPasswordHash())).isTrue();
    }

    @Test
    void roleAndPriorityInTheBodyHaveNoEffect() throws Exception {
        // Criterio de seguridad de #118: ningún campo privilegiado del JSON llega a la cuenta.
        register("""
                {"fullName":"Mala Fe","email":"malafe@uade.edu.ar","legajo":"7654321","password":"una-clave-larga",
                 "declaredUserType":"TEACHER","role":"ADMIN","priority":"TEACHER","emailVerified":true,
                 "id":"00000000-0000-0000-0000-000000000001"}""")
                .andExpect(status().isCreated());

        User user = users.findAll().getFirst();
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getId().toString()).isNotEqualTo("00000000-0000-0000-0000-000000000001");
        // Declararse docente es solo un dato: no da rol.
        assertThat(user.getDeclaredUserType()).isEqualTo(DeclaredUserType.TEACHER);
    }

    @Test
    void aRepeatedEmailIsRejectedEvenWithOtherCaseAndDoesNotCreateASecondAccount() throws Exception {
        register(json("ana@uade.edu.ar", "1111111")).andExpect(status().isCreated());

        register(json("Ana@UADE.edu.ar", "2222222"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.violations.length()").value(1))
                .andExpect(jsonPath("$.violations[0].field").value("email"));

        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void aRepeatedLegajoIsRejected() throws Exception {
        register(json("ana@uade.edu.ar", "1111111")).andExpect(status().isCreated());

        register(json("otra@uade.edu.ar", "1111111"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.violations.length()").value(1))
                .andExpect(jsonPath("$.violations[0].field").value("legajo"));

        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void reportsBothFieldsWhenEmailAndLegajoAreRepeated() throws Exception {
        register(json("ana@uade.edu.ar", "1111111")).andExpect(status().isCreated());

        register(json("ana@uade.edu.ar", "1111111"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.violations[0].field").value("email"))
                .andExpect(jsonPath("$.violations[1].field").value("legajo"));
    }

    @Test
    void anEmailFromAnotherDomainIsRejectedAndNothingIsSaved() throws Exception {
        register(json("ana@gmail.com", "1111111")).andExpect(status().isBadRequest());

        assertThat(users.count()).isZero();
    }

    @Test
    void theRawPasswordNeverReachesTheLogsEvenWithTheVerboseLevels(CapturedOutput output) throws Exception {
        // Los niveles más ruidosos de MVC y de SQL: es cuando más cosas se imprimen. Se restauran al terminar.
        List<String> loggers = List.of("org.springframework.web", "org.hibernate.SQL", "org.hibernate.orm.jdbc.bind");
        loggers.forEach(logger -> loggingSystem.setLogLevel(logger, LogLevel.TRACE));
        try {
            register("""
                    {"fullName":"Ana Pérez","email":"ana@uade.edu.ar","legajo":"1111111",
                     "password":"clave-que-no-debe-aparecer","declaredUserType":"STUDENT"}""")
                    .andExpect(status().isCreated());
        } finally {
            loggers.forEach(logger -> loggingSystem.setLogLevel(logger, null));
        }

        assertThat(output.getAll())
                // Spring sí imprimió el pedido (si no, esta prueba no probaría nada)...
                .contains("RegisterRequest[")
                // ...pero con la contraseña tapada.
                .contains("password=***")
                .doesNotContain("clave-que-no-debe-aparecer");
    }
}
