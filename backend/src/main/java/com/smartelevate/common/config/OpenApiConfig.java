package com.smartelevate.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI smartElevateOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartElevate API")
                        .description("Reserva de turnos de ascensores para reducir la congestión en el campus")
                        .version("v0"))
                // Servidor relativo: Swagger UI llama al mismo host desde donde se sirve, y la spec
                // exportada en docs/openapi.json no depende del entorno donde se generó.
                .servers(List.of(new Server().url("/").description("Mismo origen")));
    }
}
