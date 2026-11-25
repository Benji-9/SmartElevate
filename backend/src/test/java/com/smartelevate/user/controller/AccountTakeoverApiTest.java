package com.smartelevate.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

/**
 * Toma de cuenta por pre-registro (#344): alguien registra el email de otra persona con una clave propia. Quien es
 * dueño del mail no puede registrarse (409), pero con "olvidé mi contraseña" tiene que poder quedarse con la cuenta:
 * antes le llegaba un link de verificación y, al usarlo, la cuenta quedaba verificada con la clave del otro.
 */
@SpringBootTest(properties = "app.password-reset.request-cooldown=PT0S")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AccountTakeoverApiTest {

    private static final String FIRST_PASSWORD = "clave-del-primero-1";
    private static final String OWNER_PASSWORD = "clave-real-de-ana-2";
    private static final Pattern TOKEN = Pattern.compile("token=([\\w-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private EmailTokenRepository emailTokens;

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

    private ResultActions registerAna(String legajo, String password) throws Exception {
        return postJson("/api/auth/register", """
                {"fullName":"Ana Pérez","email":"ana@uade.edu.ar","legajo":"%s","password":"%s",
                 "declaredUserType":"STUDENT"}""".formatted(legajo, password));
    }

    private ResultActions loginAna(String password) throws Exception {
        return postJson("/api/auth/login", "{\"email\":\"ana@uade.edu.ar\",\"password\":\"%s\"}".formatted(password));
    }

    @Test
    void theOwnerOfTheEmailTakesTheAccountBackWithPasswordReset() throws Exception {
        // 1. Alguien se registra con el email de Ana y una clave propia.
        registerAna("1111111", FIRST_PASSWORD).andExpect(status().isCreated());
        // 2. Ana no puede registrarse: el email ya está tomado.
        registerAna("2222222", OWNER_PASSWORD).andExpect(status().isConflict());
        reset(sender);

        // 3. Ana pide "olvidé mi contraseña": le tiene que llegar un link de reset, no uno de verificación.
        postJson("/api/auth/password/forgot", "{\"email\":\"ana@uade.edu.ar\"}").andExpect(status().isAccepted());
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq("ana@uade.edu.ar"), anyString(), body.capture());
        assertThat(body.getValue()).contains("/recuperar/nueva?token=");
        Matcher token = TOKEN.matcher(body.getValue());
        assertThat(token.find()).isTrue();

        // 4. Ana elige su clave con ese link.
        postJson("/api/auth/password/reset",
                "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token.group(1), OWNER_PASSWORD))
                .andExpect(status().isNoContent());

        // 5. La clave del que se registró primero ya no sirve; la de Ana sí.
        loginAna(FIRST_PASSWORD).andExpect(status().isUnauthorized());
        loginAna(OWNER_PASSWORD).andExpect(status().isOk());
    }
}
