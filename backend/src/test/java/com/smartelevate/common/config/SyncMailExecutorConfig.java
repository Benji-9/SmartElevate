package com.smartelevate.common.config;

import java.util.concurrent.Executor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;

/**
 * Solo tests: el executor de los mails corre en el hilo que llama, así los tests de integración son deterministas.
 * Lo activa {@code test.mail.sync-executor=true} (src/test/resources/config/application.properties); en producción
 * esa propiedad no existe y rige {@link AsyncConfig}.
 */
@Configuration
@ConditionalOnProperty(name = "test.mail.sync-executor", havingValue = "true")
class SyncMailExecutorConfig {

    @Bean(AsyncConfig.MAIL_EXECUTOR)
    Executor syncMailExecutor() {
        return new SyncTaskExecutor();
    }
}
