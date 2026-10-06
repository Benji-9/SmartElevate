package com.smartelevate.common.db;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Postgres real en Docker para tests de integración (H2 no reproduce los locks de Postgres, ADR 0010).
 * Uso: anotar el test con {@code @Import(PostgresTestcontainersConfig.class)} y
 * {@code @Testcontainers(disabledWithoutDocker = true)}, para que sin Docker se saltee en vez de fallar.
 * {@code @ServiceConnection} apunta el DataSource (y Flyway) al contenedor; Spring lo reutiliza
 * entre los tests que comparten contexto.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfig {

    /** La misma imagen que docker-compose.yml. */
    private static final String IMAGE = "postgres:18-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(IMAGE);
    }
}
