package com.smartelevate.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** {@code /api/me/notification-preferences} de punta a punta (H2 + migraciones). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class NotificationPreferenceApiTest {

    private static final String URL = "/api/me/notification-preferences";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private JwtEncoder jwtEncoder;

    @AfterEach
    void clean() {
        users.deleteAll();
    }

    private String token(UUID subject) {
        Instant now = Instant.now();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(subject.toString()).issuedAt(now).expiresAt(now.plusSeconds(900))
                        .build())).getTokenValue();
    }

    private String newUserToken() {
        User u = new User("Ana Pérez", "ana@uade.edu.ar", "1234567", "x", DeclaredUserType.STUDENT, Instant.now());
        u.verifyEmail();
        return token(users.saveAndFlush(u).getId());
    }

    @Test
    void withoutTokenIs401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"departureReminder\":false,\"elevatorOutOfService\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void defaultsToAllOnAndThePutSavesThem() throws Exception {
        String token = newUserToken();

        mockMvc.perform(get(URL).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departureReminder").value(true))
                .andExpect(jsonPath("$.elevatorOutOfService").value(true));

        mockMvc.perform(put(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departureReminder\":false,\"elevatorOutOfService\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departureReminder").value(false))
                .andExpect(jsonPath("$.elevatorOutOfService").value(true));

        mockMvc.perform(get(URL).header("Authorization", token))
                .andExpect(jsonPath("$.departureReminder").value(false))
                .andExpect(jsonPath("$.elevatorOutOfService").value(true));
    }

    @Test
    void putWithAMissingOrNullFieldIs400AndSavesNothing() throws Exception {
        String token = newUserToken();

        for (String body : new String[] {"{}", "{\"departureReminder\":false}",
                "{\"departureReminder\":false,\"elevatorOutOfService\":null}"}) {
            mockMvc.perform(put(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get(URL).header("Authorization", token))
                .andExpect(jsonPath("$.departureReminder").value(true));
    }
}
