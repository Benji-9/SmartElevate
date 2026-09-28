package com.smartelevate.common.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Mantiene sincronizado el contrato versionado en {@code docs/openapi.json} con lo que genera springdoc.
 *
 * <p>Si cambiaste endpoints o DTOs y este test falla, regenerá el archivo con:
 * <pre>./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true</pre>
 * y commitealo junto con el cambio (después, {@code npm run gen:api} en frontend/).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class OpenApiSpecTest {

    private static final Path SPEC_FILE = Path.of("..", "docs", "openapi.json");
    private static final String UPDATE_COMMAND = "./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void committedSpecMatchesGeneratedSpec() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode generated = objectMapper.readTree(body);

        if (Boolean.getBoolean("openapi.update")) {
            String pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(generated)
                    .replace("\r\n", "\n");
            Files.writeString(SPEC_FILE, pretty + "\n", StandardCharsets.UTF_8);
            return;
        }

        assertThat(Files.exists(SPEC_FILE))
                .as("No existe %s. Generalo con: %s", SPEC_FILE, UPDATE_COMMAND)
                .isTrue();
        JsonNode committed = objectMapper.readTree(Files.readString(SPEC_FILE, StandardCharsets.UTF_8));
        assertThat(committed)
                .as("docs/openapi.json está desactualizado respecto del código. Regeneralo con: %s", UPDATE_COMMAND)
                .isEqualTo(generated);
    }
}
