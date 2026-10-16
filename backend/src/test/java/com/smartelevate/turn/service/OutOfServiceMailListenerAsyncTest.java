package com.smartelevate.turn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartelevate.common.config.AsyncConfig;
import com.smartelevate.turn.service.ElevatorOutageService.OutageCancellations;
import com.smartelevate.turn.service.TurnMailService.OutOfServiceNotice;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** El aviso corre en el executor propio (otro hilo) y, con la cola llena, no se pierde ni lanza (#134). */
@SpringJUnitConfig({AsyncConfig.class, OutOfServiceMailListener.class,
        OutOfServiceMailListenerAsyncTest.MailConfig.class})
@TestPropertySource(properties = {"app.mail.async.threads=1", "app.mail.async.queue=0"})
class OutOfServiceMailListenerAsyncTest {

    static final Map<String, String> THREAD_BY_ELEVATOR = new ConcurrentHashMap<>();
    static final CountDownLatch FIRST_STARTED = new CountDownLatch(1);
    static final CountDownLatch RELEASE_FIRST = new CountDownLatch(1);
    static final CountDownLatch FIRST_DONE = new CountDownLatch(1);

    @Configuration
    static class MailConfig {
        @Bean
        TurnMailService turnMailService() {
            TurnMailService mails = mock(TurnMailService.class);
            when(mails.sendElevatorOutOfService(any())).thenAnswer(inv -> {
                OutOfServiceNotice n = inv.getArgument(0);
                THREAD_BY_ELEVATOR.put(n.elevatorCode(), Thread.currentThread().getName());
                if (n.elevatorCode().equals("A")) {
                    FIRST_STARTED.countDown();
                    RELEASE_FIRST.await(30, TimeUnit.SECONDS);
                    FIRST_DONE.countDown();
                }
                return true;
            });
            return mails;
        }
    }

    @Autowired
    private OutOfServiceMailListener listener;

    private static OutageCancellations event(String elevator) {
        return new OutageCancellations(List.of(new OutOfServiceNotice(UUID.randomUUID(), Instant.now(), elevator,
                "Independencia 2", "Independencia")));
    }

    @Test
    void runsOnTheMailExecutorAndAFullQueueRunsInTheCallerWithoutLosingNorThrowing() throws Exception {
        String caller = Thread.currentThread().getName();

        listener.onCancellations(event("A")); // vuelve enseguida: lo atiende el hilo "mail-"
        assertThat(FIRST_STARTED.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(THREAD_BY_ELEVATOR.get("A")).startsWith("mail-").isNotEqualTo(caller);

        // El único hilo está ocupado y la cola no tiene lugar: se rechaza y corre en este hilo, sin excepción.
        assertThatCode(() -> listener.onCancellations(event("B"))).doesNotThrowAnyException();
        assertThat(THREAD_BY_ELEVATOR.get("B")).isEqualTo(caller);

        RELEASE_FIRST.countDown();
        assertThat(FIRST_DONE.await(30, TimeUnit.SECONDS)).isTrue();
    }
}
