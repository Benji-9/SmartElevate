package com.smartelevate.elevator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.smartelevate.elevator.repository.KioskRepository;
import com.smartelevate.elevator.service.InvalidQrTokenException;
import com.smartelevate.elevator.service.QrTokenService;
import com.smartelevate.priority.repository.PriorityAuditRepository;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.Role;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Kioscos de punta a punta (H2 + migraciones): alta, credencial de dispositivo, token del QR y revocación. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class KioskApiTest {

    private static final String QR_TOKEN = "/api/kiosks/me/qr-token";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private KioskRepository kiosks;
    @Autowired
    private PriorityAuditRepository audit;
    @Autowired
    private QrTokenService qrTokens;
    @Autowired
    private JwtEncoder jwtEncoder;

    private User admin;
    private User common;

    @AfterEach
    void clean() {
        audit.deleteAll();
        kiosks.deleteAll();
        users.deleteAll();
    }

    @BeforeEach
    void seed() {
        clean();
        admin = newUser("admin@uade.edu.ar", "2000001", Role.ADMIN);
        common = newUser("comun@uade.edu.ar", "2000002", Role.USER);
    }

    private User newUser(String email, String legajo, Role role) {
        User u = new User("Usuario " + legajo, email, legajo, "x", DeclaredUserType.STUDENT, Instant.now());
        u.verifyEmail();
        u.assignRole(role);
        return users.saveAndFlush(u);
    }

    private String jwt(User u) {
        Instant now = Instant.now();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(u.getId().toString()).issuedAt(now)
                        .expiresAt(now.plusSeconds(900)).build())).getTokenValue();
    }

    private MockHttpServletRequestBuilder as(User u, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", jwt(u)).contentType(MediaType.APPLICATION_JSON);
    }

    /** Crea un kiosco como ADMIN y devuelve la respuesta del alta (con el secreto). */
    private String create(String elevator) throws Exception {
        return mockMvc.perform(as(admin, post("/api/admin/kiosks"))
                        .content("{\"elevatorId\":\"" + elevator + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.elevatorId").value(elevator))
                .andExpect(jsonPath("$.simulated").value(false))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void kioskGetsATokenForTheElevatorOfItsCredential() throws Exception {
        String created = create("33");
        String secret = JsonPath.read(created, "$.secret");

        // El parámetro se ignora: el ascensor sale de la credencial.
        String body = mockMvc.perform(get(QR_TOKEN).param("elevatorId", "34")
                        .header("Authorization", "Bearer " + secret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.expiresAt").isString())
                .andExpect(jsonPath("$.elevatorId").value("33"))
                .andReturn().getResponse().getContentAsString();

        var claims = qrTokens.validate(JsonPath.read(body, "$.token"));
        assertThat(claims.elevatorId()).isEqualTo("33");
        assertThat(claims.kioskId().toString()).isEqualTo(JsonPath.read(created, "$.id"));
        assertThat(kiosks.findAll().getFirst().getLastSeenAt()).isNotNull();
    }

    @Test
    void kioskEndpointRejectsMissingWrongAndUserCredentials() throws Exception {
        create("33");
        for (var request : new MockHttpServletRequestBuilder[] {
                get(QR_TOKEN),
                get(QR_TOKEN).header("Authorization", "Bearer no-es-un-secreto"),
                get(QR_TOKEN).header("Authorization", "Bearer "),
                // Un JWT de usuario no sirve como credencial de kiosco.
                get(QR_TOKEN).header("Authorization", jwt(admin))}) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("KIOSK_UNAUTHORIZED"));
        }
    }

    @Test
    void revokedKioskStopsWorkingRightAway() throws Exception {
        String created = create("33");
        String secret = JsonPath.read(created, "$.secret");
        String id = JsonPath.read(created, "$.id");
        String token = JsonPath.read(mockMvc.perform(get(QR_TOKEN).header("Authorization", "Bearer " + secret))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");

        mockMvc.perform(as(admin, post("/api/admin/kiosks/" + id + "/revoke"))).andExpect(status().isNoContent());

        mockMvc.perform(get(QR_TOKEN).header("Authorization", "Bearer " + secret))
                .andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> qrTokens.validate(token)).isInstanceOf(InvalidQrTokenException.class);
        mockMvc.perform(as(admin, get("/api/admin/kiosks")))
                .andExpect(jsonPath("$.kiosks[0].revokedAt").isString());
        assertThat(audit.findAll()).extracting(a -> a.getAction() + " " + a.getTargetKioskId())
                .containsExactlyInAnyOrder("KIOSK_CREATED " + id, "KIOSK_REVOKED " + id);
    }

    @Test
    void listNeverExposesTheSecretOrItsHash() throws Exception {
        String secret = JsonPath.read(create("33"), "$.secret");
        String hash = kiosks.findAll().getFirst().getSecretHash();

        String list = mockMvc.perform(as(admin, get("/api/admin/kiosks")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulatorEnabled").value(false))
                .andExpect(jsonPath("$.kiosks[0].elevatorId").value("33"))
                .andExpect(jsonPath("$.kiosks[0].secret").doesNotExist())
                .andExpect(jsonPath("$.kiosks[0].secretHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(secret).doesNotContain(hash);
    }

    @Test
    void simulatedKioskIsRejectedWhileTheSimulatorIsOff() throws Exception {
        mockMvc.perform(as(admin, post("/api/admin/kiosks")).content("{\"elevatorId\":\"33\",\"simulated\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SIMULATOR_DISABLED"));
        assertThat(kiosks.count()).isZero();
    }

    @Test
    void adminValidationsAndErrors() throws Exception {
        mockMvc.perform(as(admin, post("/api/admin/kiosks")).content("{\"elevatorId\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(admin, post("/api/admin/kiosks")).content("{\"elevatorId\":\"99\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ELEVATOR_NOT_FOUND"));
        mockMvc.perform(as(admin, post("/api/admin/kiosks/00000000-0000-0000-0000-000000000000/revoke")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KIOSK_NOT_FOUND"));
    }

    @Test
    void adminEndpointsNeedAnAdmin() throws Exception {
        String id = JsonPath.read(create("33"), "$.id");
        var endpoints = new MockHttpServletRequestBuilder[] {
                get("/api/admin/kiosks"),
                post("/api/admin/kiosks").content("{\"elevatorId\":\"33\"}"),
                post("/api/admin/kiosks/" + id + "/revoke")};
        for (var request : endpoints) {
            mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
            mockMvc.perform(as(common, request)).andExpect(status().isForbidden());
        }
    }
}
