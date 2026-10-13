package com.smartelevate.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";

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
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Access token del login: Authorization: Bearer <accessToken>")))
                .servers(List.of(new Server().url("/").description("Mismo origen")));
    }
}
