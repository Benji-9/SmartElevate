package com.smartelevate.common.config;

import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Habilita {@code @Async} con un executor acotado propio para los mails masivos (#134): un SMTP lento no retiene
 * conexiones del pool ni el request del admin. Pocos hilos y una cola chica; si se llena, el envío se descarta con un
 * log (los reservantes igual ven la cancelación en la app). Con {@code app.mail.synchronous=true} corre en el hilo
 * que llama: los tests lo usan para ser deterministas.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String MAIL_EXECUTOR = "mailExecutor";

    private static final int MAIL_THREADS = 2;
    private static final int MAIL_QUEUE = 50;

    @Bean(MAIL_EXECUTOR)
    Executor mailExecutor(@Value("${app.mail.synchronous:false}") boolean synchronous) {
        if (synchronous) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mail-");
        executor.setCorePoolSize(MAIL_THREADS);
        executor.setMaxPoolSize(MAIL_THREADS);
        executor.setQueueCapacity(MAIL_QUEUE);
        executor.setRejectedExecutionHandler((task, pool) -> log.warn("Cola de mails llena: se descarta un envío."));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }
}
