package com.smartelevate.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartelevate.priority.model.PriorityCategory;
import com.smartelevate.priority.model.PriorityStatus;
import com.smartelevate.priority.model.UserPriority;
import com.smartelevate.priority.repository.UserPriorityRepository;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** {@code GET /api/users/me} de punta a punta (H2 + migraciones): 401 sin token y nada sale de los claims. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class UsersMeApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private UserPriorityRepository priorities;
    @Autowired
    private JwtEncoder jwtEncoder;

    @AfterEach
    void clean() {
        priorities.deleteAll();
        users.deleteAll();
    }

    private String token(UUID subject) {
        Instant now = Instant.now();
        // Claims de rol y prioridad falsos: el servidor tiene que ignorarlos.
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(subject.toString()).issuedAt(now).expiresAt(now.plusSeconds(900))
                        .claim("role", "ADMIN").claim("priority", "REDUCED_MOBILITY").build())).getTokenValue();
    }

    @Test
    void withoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfAnUnknownUserIs401() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", token(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsRoleAndCurrentPriorityFromTheServerNotFromClaims() throws Exception {
        User u = new User("Ana Pérez", "ana@uade.edu.ar", "1234567", "x", DeclaredUserType.TEACHER, Instant.now());
        u.verifyEmail();
        User user = users.saveAndFlush(u);

        mockMvc.perform(get("/api/users/me").header("Authorization", token(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("ana@uade.edu.ar"))
                .andExpect(jsonPath("$.fullName").value("Ana Pérez"))
                .andExpect(jsonPath("$.legajo").value("1234567"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.priority").value("NONE"))
                .andExpect(jsonPath("$.declaredUserType").value("TEACHER"));

        UserPriority teacher = new UserPriority(user.getId(), PriorityCategory.TEACHER);
        teacher.decide(PriorityStatus.APPROVED, null, Instant.now(), user.getId());
        priorities.saveAndFlush(teacher);

        mockMvc.perform(get("/api/users/me").header("Authorization", token(user.getId())))
                .andExpect(jsonPath("$.priority").value("TEACHER"));
    }
}
