package com.smartelevate.priority.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartelevate.priority.model.PriorityCategory;
import com.smartelevate.priority.model.PriorityStatus;
import com.smartelevate.priority.model.UserPriority;
import com.smartelevate.priority.repository.PriorityAuditRepository;
import com.smartelevate.priority.repository.UserPriorityRepository;
import com.smartelevate.priority.service.PriorityService;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.Role;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Endpoints de ADMIN de punta a punta (H2 + migraciones): seguridad por endpoint, flujo y auditoría. */
@SpringBootTest(properties = "app.admin.user-search.page-size=2")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(AdminPriorityApiTest.FixedClockConfig.class)
class AdminPriorityApiTest {

    // Martes 2026-10-13 12:00 en Buenos Aires: en el cuatrimestre que cierra el 2026-12-31 (seed de V18).
    private static final Instant NOW = Instant.parse("2026-10-13T15:00:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private UserPriorityRepository priorities;
    @Autowired
    private PriorityAuditRepository audit;
    @Autowired
    private PriorityService priorityService;
    @Autowired
    private JwtEncoder jwtEncoder;

    private User admin;
    private User reviewer;
    private User common;
    private User teacher;

    @AfterEach
    void clean() {
        audit.deleteAll();
        priorities.deleteAll();
        users.deleteAll();
    }

    private User newUser(String email, String legajo, DeclaredUserType type, Role role) {
        User u = new User("Usuario " + legajo, email, legajo, "x", type, Instant.now());
        u.verifyEmail();
        u.assignRole(role);
        return users.saveAndFlush(u);
    }

    @BeforeEach
    void seed() {
        clean();
        admin = newUser("admin@uade.edu.ar", "1000001", DeclaredUserType.STUDENT, Role.ADMIN);
        reviewer = newUser("rev@uade.edu.ar", "1000002", DeclaredUserType.STUDENT, Role.REVIEWER);
        common = newUser("comun@uade.edu.ar", "1000003", DeclaredUserType.STUDENT, Role.USER);
        teacher = newUser("doc@uade.edu.ar", "1000004", DeclaredUserType.TEACHER, Role.USER);
    }

    private String token(User u) {
        Instant now = Instant.now();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(u.getId().toString()).issuedAt(now)
                        .expiresAt(now.plusSeconds(900)).build())).getTokenValue();
    }

    private MockHttpServletRequestBuilder as(User u, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", token(u)).contentType(MediaType.APPLICATION_JSON);
    }

    private MockHttpServletRequestBuilder[] allEndpoints() {
        String id = teacher.getId().toString();
        return new MockHttpServletRequestBuilder[] {
                get("/api/admin/teachers/pending"),
                post("/api/admin/teachers/" + id + "/approve"),
                post("/api/admin/teachers/" + id + "/reject"),
                get("/api/admin/priority-users"),
                get("/api/admin/users?query=usuario"),
                delete("/api/admin/priority-users/" + id),
                put("/api/admin/users/" + id + "/role").content("{\"role\":\"ADMIN\"}")
        };
    }

    @Test
    void withoutTokenEveryEndpointIs401() throws Exception {
        for (var request : allEndpoints()) {
            mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void commonUsersAndReviewersGet403OnEveryEndpoint() throws Exception {
        for (User caller : new User[] {common, reviewer}) {
            for (var request : allEndpoints()) {
                mockMvc.perform(as(caller, request)).andExpect(status().isForbidden());
            }
        }
        // Ni se dio un rol ni una prioridad a sí mismo.
        assertThat(users.findById(teacher.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
        assertThat(priorityService.currentPriority(teacher.getId())).isEqualTo(PriorityCategory.NONE);
        assertThat(audit.count()).isZero();
    }

    @Test
    void adminApprovesTeacherEndToEnd() throws Exception {
        mockMvc.perform(as(admin, get("/api/admin/teachers/pending")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(teacher.getId().toString()))
                .andExpect(jsonPath("$[0].legajo").value("1000004"))
                .andExpect(jsonPath("$[0].registeredAt").exists());

        mockMvc.perform(as(admin, post("/api/admin/teachers/" + teacher.getId() + "/approve")))
                .andExpect(status().isNoContent());

        assertThat(priorityService.currentPriority(teacher.getId())).isEqualTo(PriorityCategory.TEACHER);
        // Vence al fin del cierre del cuatrimestre (31/12 en Buenos Aires = 01/01 03:00 UTC).
        assertThat(priorities.findByUserIdAndCategory(teacher.getId(), PriorityCategory.TEACHER).orElseThrow()
                .getExpiresAt()).isEqualTo(Instant.parse("2027-01-01T03:00:00Z"));
        mockMvc.perform(as(admin, get("/api/admin/teachers/pending")))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(as(admin, get("/api/admin/priority-users")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].category").value("TEACHER"));
        mockMvc.perform(as(admin, post("/api/admin/teachers/" + teacher.getId() + "/approve")))
                .andExpect(status().isConflict());

        mockMvc.perform(as(admin, delete("/api/admin/priority-users/" + teacher.getId())))
                .andExpect(status().isNoContent());
        assertThat(priorityService.currentPriority(teacher.getId())).isEqualTo(PriorityCategory.NONE);
        mockMvc.perform(as(admin, delete("/api/admin/priority-users/" + teacher.getId())))
                .andExpect(status().isNotFound());

        assertThat(audit.findByTargetUserIdOrderByCreatedAtAsc(teacher.getId()))
                .extracting("action", "actorId")
                .containsExactly(org.assertj.core.api.Assertions.tuple("TEACHER_APPROVED", admin.getId()),
                        org.assertj.core.api.Assertions.tuple("PRIORITY_REVOKED", admin.getId()));
    }

    @Test
    void anExpiredTeacherReturnsToTheQueueAndCanBeRenewed() throws Exception {
        UserPriority expired = new UserPriority(teacher.getId(), PriorityCategory.TEACHER);
        expired.decide(PriorityStatus.APPROVED, NOW, NOW.minusSeconds(3600), admin.getId());
        priorities.saveAndFlush(expired);

        mockMvc.perform(as(admin, get("/api/admin/teachers/pending")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(teacher.getId().toString()));
        mockMvc.perform(as(admin, post("/api/admin/teachers/" + teacher.getId() + "/approve")))
                .andExpect(status().isNoContent());

        assertThat(priorityService.currentPriority(teacher.getId())).isEqualTo(PriorityCategory.TEACHER);
        mockMvc.perform(as(admin, get("/api/admin/teachers/pending"))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rejectedTeacherLeavesTheQueueWithoutPriority() throws Exception {
        mockMvc.perform(as(admin, post("/api/admin/teachers/" + teacher.getId() + "/reject")))
                .andExpect(status().isNoContent());

        assertThat(priorityService.currentPriority(teacher.getId())).isEqualTo(PriorityCategory.NONE);
        mockMvc.perform(as(admin, get("/api/admin/teachers/pending"))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void approvingAStudentOrUnknownUser() throws Exception {
        mockMvc.perform(as(admin, post("/api/admin/teachers/" + common.getId() + "/approve")))
                .andExpect(status().isConflict());
        mockMvc.perform(as(admin, post("/api/admin/teachers/" + UUID.randomUUID() + "/approve")))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminAssignsRoleAndItAppliesToTheNextRequest() throws Exception {
        mockMvc.perform(as(admin, put("/api/admin/users/" + common.getId() + "/role").content("{\"role\":\"ADMIN\"}")))
                .andExpect(status().isNoContent());

        // El rol sale de la base: el mismo usuario, con un token de antes, ya es ADMIN.
        mockMvc.perform(as(common, get("/api/admin/priority-users"))).andExpect(status().isOk());
        assertThat(audit.findByTargetUserIdOrderByCreatedAtAsc(common.getId()))
                .extracting("action", "detail").containsExactly(
                        org.assertj.core.api.Assertions.tuple("ROLE_CHANGED", "USER->ADMIN"));
    }

    @Test
    void roleValidationAndSelfChange() throws Exception {
        mockMvc.perform(as(admin, put("/api/admin/users/" + common.getId() + "/role").content("{\"role\":\"BOSS\"}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(admin, put("/api/admin/users/" + common.getId() + "/role").content("{}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(admin, put("/api/admin/users/" + admin.getId() + "/role").content("{\"role\":\"USER\"}")))
                .andExpect(status().isConflict());
    }

    private String search(String query) throws Exception {
        return mockMvc.perform(as(admin, get("/api/admin/users").param("query", query)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void searchFindsByEmailNameOrLegajoIgnoringCase() throws Exception {
        mockMvc.perform(as(admin, get("/api/admin/users").param("query", "COMUN@")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(common.getId().toString()))
                .andExpect(jsonPath("$.items[0].role").value("USER"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        mockMvc.perform(as(admin, get("/api/admin/users").param("query", "1000002")))
                .andExpect(jsonPath("$.items[0].email").value("rev@uade.edu.ar"))
                .andExpect(jsonPath("$.items[0].role").value("REVIEWER"));
        mockMvc.perform(as(admin, get("/api/admin/users").param("query", "USUARIO 1000004")))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(teacher.getId().toString()));
    }

    @Test
    void searchTreatsWildcardsAsText() throws Exception {
        mockMvc.perform(as(admin, get("/api/admin/users").param("query", "%%%")))
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(as(admin, get("/api/admin/users").param("query", "___")))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void searchPaginatesByCursor() throws Exception {
        String first = search("usuario");
        var json = new tools.jackson.databind.ObjectMapper().readTree(first);
        assertThat(json.get("items")).hasSize(2);
        String cursor = json.get("nextCursor").asString();

        var second = new tools.jackson.databind.ObjectMapper().readTree(
                mockMvc.perform(as(admin, get("/api/admin/users").param("query", "usuario").param("cursor", cursor)))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(second.get("items")).hasSize(2);
        assertThat(second.get("nextCursor").isNull()).isTrue();
        assertThat(second.get("items").get(0).get("id").asString())
                .isNotEqualTo(json.get("items").get(0).get("id").asString())
                .isNotEqualTo(json.get("items").get(1).get("id").asString());
    }

    @Test
    void searchNeedsMinimumLengthAndValidCursor() throws Exception {
        mockMvc.perform(as(admin, get("/api/admin/users").param("query", "ab"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(admin, get("/api/admin/users").param("query", "  a "))).andExpect(status().isBadRequest());
        mockMvc.perform(as(admin, get("/api/admin/users"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(admin, get("/api/admin/users").param("query", "a".repeat(101))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(admin, get("/api/admin/users").param("query", "a".repeat(100))))
                .andExpect(status().isOk());
        mockMvc.perform(as(admin, get("/api/admin/users").param("query", "usuario").param("cursor", "x")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchDoesNotExposeSensitiveFields() throws Exception {
        String body = search("usuario");
        assertThat(body).doesNotContain("passwordHash", "password", "declaredUserType", "certificate", "priority");
        mockMvc.perform(as(admin, get("/api/admin/users").param("query", "comun@")))
                .andExpect(jsonPath("$.items[0].keys()").value(
                        org.hamcrest.Matchers.containsInAnyOrder("id", "fullName", "email", "legajo", "role", "userType")));
    }
}
