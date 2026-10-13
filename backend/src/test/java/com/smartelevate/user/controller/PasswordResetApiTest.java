package com.smartelevate.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartelevate.common.mail.EmailSender;
import com.smartelevate.user.repository.EmailTokenRepository;
import com.smartelevate.user.repository.RefreshTokenRepository;
import com.smartelevate.user.repository.UserRepository;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** Olvidé mi contraseña de punta a punta, con la base real. Sin espera entre pedidos para poder pedir dos veces. */
@SpringBootTest(properties = "app.password-reset.request-cooldown=PT0S")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PasswordResetApiTest {

    private static final String OLD = "una-clave-larga";
    private static final String NEW = "otra-clave-nueva";
    private static final Pattern TOKEN = Pattern.compile("token=([\\w-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private EmailTokenRepository emailTokens;

    @Autowired
    private JsonMapper json;

    @MockitoBean
    private EmailSender sender;

    @BeforeEach
    @AfterEach
    void clean() {
        reset(sender);
        refreshTokens.deleteAll();
        emailTokens.deleteAll();
        users.deleteAll();
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void registerAna() throws Exception {
        postJson("/api/auth/register", """
                {"fullName":"Ana Pérez","email":"ana@uade.edu.ar","legajo":"1111111","password":"%s",
                 "declaredUserType":"STUDENT"}""".formatted(OLD)).andExpect(status().isCreated());
        var ana = users.findByEmail("ana@uade.edu.ar").orElseThrow();
        ana.verifyEmail();
        users.saveAndFlush(ana);
        reset(sender);
    }

    private ResultActions login(String password) throws Exception {
        return postJson("/api/auth/login", "{\"email\":\"ana@uade.edu.ar\",\"password\":\"%s\"}".formatted(password));
    }

    private String forgotAndReadToken() throws Exception {
        postJson("/api/auth/password/forgot", "{\"email\":\"ana@uade.edu.ar\"}").andExpect(status().isAccepted());
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).send(eq("ana@uade.edu.ar"), anyString(), body.capture());
        Matcher m = TOKEN.matcher(body.getValue());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private ResultActions resetWith(String token, String password) throws Exception {
        return postJson("/api/auth/password/reset", "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, password));
    }

    @Test
    void forgotLooksTheSameForAnExistingAndAnUnknownEmail() throws Exception {
        registerAna();

        String known = postJson("/api/auth/password/forgot", "{\"email\":\"ana@uade.edu.ar\"}")
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknown = postJson("/api/auth/password/forgot", "{\"email\":\"nadie@uade.edu.ar\"}")
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(known).isEqualTo(unknown);
        verify(sender).send(eq("ana@uade.edu.ar"), anyString(), anyString());
        verify(sender, never()).send(eq("nadie@uade.edu.ar"), anyString(), anyString());
    }

    @Test
    void resetChangesThePasswordAndClosesEverySession() throws Exception {
        registerAna();
        String refresh = json.readTree(login(OLD).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString()).get("refreshToken").asString();

        resetWith(forgotAndReadToken(), NEW).andExpect(status().isNoContent());

        login(OLD).andExpect(status().isUnauthorized());
        login(NEW).andExpect(status().isOk());
        postJson("/api/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theTokenWorksOnlyOnce() throws Exception {
        registerAna();
        String token = forgotAndReadToken();

        resetWith(token, NEW).andExpect(status().isNoContent());
        resetWith(token, "tercera-clave-1").andExpect(status().isBadRequest());

        login(NEW).andExpect(status().isOk());
    }

    @Test
    void askingAgainInvalidatesThePreviousLink() throws Exception {
        registerAna();
        String first = forgotAndReadToken();
        reset(sender);
        String second = forgotAndReadToken();

        resetWith(first, NEW).andExpect(status().isBadRequest());
        resetWith(second, NEW).andExpect(status().isNoContent());
    }

    @Test
    void aWeakNewPasswordIsRejectedAndKeepsTheTokenUsable() throws Exception {
        registerAna();
        String token = forgotAndReadToken();

        resetWith(token, "corta").andExpect(status().isBadRequest());

        resetWith(token, NEW).andExpect(status().isNoContent());
    }

    @Test
    void anUnknownTokenIs400() throws Exception {
        resetWith("no-es-un-token-real", NEW).andExpect(status().isBadRequest());
    }
}
