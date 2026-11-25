package com.smartelevate.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Los errores de Spring MVC llegan al cliente con su status real y el ApiError, por HTTP de punta a punta (#345). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ErrorResponsesApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aBodyWithAnUnsupportedContentTypeIsA415WithTheStandardError() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }
}
