package com.smartelevate.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";
    public static final String KIOSK = "kioskAuth";

    @Bean
    public OpenAPI smartElevateOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartElevate API")
                        .description("Reserva de turnos de ascensores para reducir la congestión en el campus")
                        .version("v0"))
                // Servidor relativo: Swagger UI llama al mismo host desde donde se sirve, y la spec
                // exportada en docs/openapi.json no depende del entorno donde se generó.
                // Esquema de seguridad: los endpoints protegidos lo declaran con
                // @SecurityRequirement(name = OpenApiConfig.BEARER) (ver SecurityConfig).
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("Access token del login: Authorization: Bearer <accessToken>"))
                        .addSecuritySchemes(KIOSK, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer")
                                .description("Credencial del kiosco (ADR 0020): Authorization: Bearer <secret>, "
                                        + "el secreto que devolvió el alta del kiosco")))
                .servers(List.of(new Server().url("/").description("Mismo origen")));
    }

    /**
     * Un DTO anidado con {@code @Schema(nullable = true)} sale como {@code $ref} + {@code type: null}, que los
     * generadores ignoran. Lo reescribe como {@code anyOf: [$ref, null]} (OpenAPI 3.1).
     */
    @Bean
    public OpenApiCustomizer nullableRefs() {
        return openApi -> openApi.getComponents().getSchemas().values().forEach(schema -> {
            Map<String, Schema<?>> properties = schema.getProperties();
            if (properties != null) {
                properties.replaceAll((name, p) -> p.get$ref() != null && p.getTypes() != null
                        && p.getTypes().contains("null")
                        ? new Schema<>().description(p.getDescription()).anyOf(
                                List.of(new Schema<>().$ref(p.get$ref()), new Schema<>().types(Set.of("null"))))
                        : p);
            }
        });
    }
}
