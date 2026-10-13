package com.smartelevate.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartelevate.common.security.CurrentUser;
import com.smartelevate.user.model.Role;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.RefreshTokenRepository;
import com.smartelevate.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Login, refresh, logout y reglas de acceso de punta a punta, con la base real (H2 + migraciones). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(SessionApiTest.ProbeController.class)
class SessionApiTest {

    /** Endpoints de prueba: sirven de ejemplo de cómo se protege una ruta y se obtiene al usuario actual. */
    @RestController
    static class ProbeController {

        @GetMapping("/api/probe/me")
        public String me(@AuthenticationPrincipal CurrentUser user) {
            return user.id() + ":" + user.role();
        }

        @GetMapping("/api/probe/admin")
        @PreAuthorize("hasRole('ADMIN')")
        public String admin() {
            return "ok";
        }
    }

    private static final String PASSWORD = "una-clave-larga";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private JsonMapper json;

    @Autowired
    private JwtEncoder jwtEncoder;

    private User ana;

    @BeforeEach
    @AfterEach
    void clean() {
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    private void registerAna(boolean verified) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Ana Pérez","email":"ana@uade.edu.ar","legajo":"1111111","password":"%s",
                 "declaredUserType":"STUDENT"}""".formatted(PASSWORD))).andExpect(status().isCreated());
        ana = users.findByEmail("ana@uade.edu.ar").orElseThrow();
        if (verified) {
            ana.verifyEmail();
            users.saveAndFlush(ana);
        }
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private JsonNode loginOk() throws Exception {
        return json.readTree(login("ana@uade.edu.ar", PASSWORD).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
    }

    private ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(token)));
    }

    private ResultActions getWith(String path, String accessToken) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + accessToken));
    }

    @Test
    void loginReturnsTokensAndTheAccessTokenWorks() throws Exception {
        registerAna(true);
        JsonNode tokens = loginOk();

        assertThat(tokens.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(tokens.get("expiresIn").asLong()).isEqualTo(900);
        getWith("/api/probe/me", tokens.get("accessToken").asString()).andExpect(status().isOk());
    }

    @Test
    void accessTokenPayloadHasNoRoleNorPriority() throws Exception {
        registerAna(true);
        String access = loginOk().get("accessToken").asString();

        String payload = new String(Base64.getUrlDecoder().decode(access.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(json.readTree(payload).propertyNames()).containsExactlyInAnyOrder("sub", "iat", "exp");
    }

    @Test
    void badCredentialsAreIndistinguishableForUnknownEmailAndWrongPassword() throws Exception {
        registerAna(true);

        String wrong = login("ana@uade.edu.ar", "otra-clave-larga").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknown = login("nadie@uade.edu.ar", "otra-clave-larga").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(wrong).get("message")).isEqualTo(json.readTree(unknown).get("message"));
    }

    @Test
    void unverifiedAccountCannotLogIn() throws Exception {
        registerAna(false);

        login("ana@uade.edu.ar", PASSWORD).andExpect(status().isForbidden());
    }

    @Test
    void loginValidatesTheBody() throws Exception {
        login("", "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        registerAna(true);
        String access = loginOk().get("accessToken").asString();

        mockMvc.perform(get("/api/probe/me")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
        // Manipulado: se cambia un carácter de la firma.
        String tampered = access.substring(0, access.length() - 2) + (access.endsWith("A") ? "B" : "A") + "x";
        getWith("/api/probe/me", tampered).andExpect(status().isUnauthorized());
        getWith("/api/probe/me", "basura").andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredAccessTokenIsRejected() throws Exception {
        registerAna(true);
        // Firmado con la clave de la app, pero vencido hace 5 minutos (más que la tolerancia de reloj de 60 s).
        Instant exp = Instant.now().minusSeconds(300);
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(ana.getId().toString()).issuedAt(exp.minusSeconds(900))
                        .expiresAt(exp).build())).getTokenValue();

        getWith("/api/probe/me", expired).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenForADeletedUserIsRejected() throws Exception {
        registerAna(true);
        String access = loginOk().get("accessToken").asString();
        refreshTokens.deleteAll();
        users.deleteAll();

        getWith("/api/probe/me", access).andExpect(status().isUnauthorized());
    }

    @Test
    void roleComesFromTheDatabaseNotFromTheToken() throws Exception {
        registerAna(true);
        String access = loginOk().get("accessToken").asString();

        getWith("/api/probe/admin", access).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(get("/api/probe/admin")).andExpect(status().isUnauthorized());

        // Un ADMIN la promueve: rige con el mismo token, sin volver a loguearse.
        ReflectionTestUtils.setField(ana, "role", Role.ADMIN);
        users.saveAndFlush(ana);
        getWith("/api/probe/admin", access).andExpect(status().isOk());
    }

    @Test
    void refreshRotatesAndReusingTheOldTokenKillsTheSession() throws Exception {
        registerAna(true);
        String first = loginOk().get("refreshToken").asString();

        JsonNode second = json.readTree(refresh(first).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(second.get("refreshToken").asString()).isNotEqualTo(first);

        // El viejo ya no sirve y, como alguien lo reusó, el nuevo de su familia también se revoca.
        refresh(first).andExpect(status().isUnauthorized());
        refresh(second.get("refreshToken").asString()).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithGarbageIsUnauthorized() throws Exception {
        refresh("no-existe").andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        registerAna(true);
        String refresh = loginOk().get("refreshToken").asString();

        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refresh))).andExpect(status().isNoContent());
        refresh(refresh).andExpect(status().isUnauthorized());
        // Idempotente.
        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refresh))).andExpect(status().isNoContent());
    }

    @Test
    void publicEndpointsNeedNoToken() throws Exception {
        mockMvc.perform(get("/api/ping")).andExpect(status().isOk());
        mockMvc.perform(get("/api/elevators")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths").exists());
        mockMvc.perform(get("/api/turns")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/no-existe")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(not("")));
    }
}
