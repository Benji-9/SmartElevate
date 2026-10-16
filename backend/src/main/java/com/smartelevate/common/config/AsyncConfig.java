package com.smartelevate.common.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Habilita {@code @Async} con un executor acotado propio para los mails masivos (#134): un SMTP lento no retiene
 * conexiones del pool ni el request del admin. Hilos y cola vienen de {@code app.mail.async.threads} y
 * {@code app.mail.async.queue}. Si la cola se llena, el envío corre en el hilo que lo pidió
 * ({@link ThreadPoolExecutor.CallerRunsPolicy}): no se pierden avisos, solo se frena a quien los pide. Al apagar, espera
 * hasta 30 s a los envíos en curso. Los tests lo reemplazan por uno síncrono ({@code test.mail.sync-executor=true}).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String MAIL_EXECUTOR = "mailExecutor";

    private static final int SHUTDOWN_WAIT_SECONDS = 30;

    @Bean(MAIL_EXECUTOR)
    @ConditionalOnProperty(name = "test.mail.sync-executor", havingValue = "false", matchIfMissing = true)
    Executor mailExecutor(@Value("${app.mail.async.threads:2}") int threads,
                          @Value("${app.mail.async.queue:50}") int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mail-");
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(queue);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
        return executor;
    }
}
