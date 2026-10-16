package com.smartelevate.common.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Habilita {@code @Async} con un executor acotado propio para los mails masivos (#134): un SMTP lento no retiene
 * conexiones del pool ni el request del admin. Hilos (mínimo 1) y cola (mínimo 0) vienen de
 * {@link MailAsyncProperties}. Si la cola se llena, el envío corre en el hilo que lo pidió
 * ({@link ThreadPoolExecutor.CallerRunsPolicy}): no se pierden avisos, solo se frena a quien los pide. Al apagar, espera
 * hasta 30 s a los envíos en curso. Los tests lo reemplazan por uno síncrono ({@code test.mail.sync-executor=true}).
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(MailAsyncProperties.class)
public class AsyncConfig {

    public static final String MAIL_EXECUTOR = "mailExecutor";

    private static final int SHUTDOWN_WAIT_SECONDS = 30;

    @Bean(MAIL_EXECUTOR)
    @ConditionalOnProperty(name = "test.mail.sync-executor", havingValue = "false", matchIfMissing = true)
    Executor mailExecutor(MailAsyncProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mail-");
        executor.setCorePoolSize(props.threads());
        executor.setMaxPoolSize(props.threads());
        executor.setQueueCapacity(props.queue());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
        return executor;
    }
}
