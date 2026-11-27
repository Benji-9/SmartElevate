package com.smartelevate.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartelevate.user.repository.LoginFailureRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/** Contra la base real, como {@link LoginThrottleTest}: comparten la tabla y la poda. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RequestThrottleTest {

    private static final Instant T0 = Instant.parse("2026-11-26T13:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String IP = "203.0.113.7";
    private static final LoginThrottleProperties LOGIN = new LoginThrottleProperties(WINDOW, 10, 100);

    @Autowired
    private LoginFailureRepository rows;

    @BeforeEach
    void setUp() {
        rows.deleteAll();
    }

    private RequestThrottle at(Instant now) {
        return new RequestThrottle(rows, new RequestThrottleProperties(WINDOW, 3), LOGIN, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void theRequestAfterTheCapWaitsUntilTheOldestLeavesTheWindowAndIsNotCounted() {
        at(T0).check("register", IP);
        at(T0.plusSeconds(60)).check("register", IP);
        at(T0.plusSeconds(120)).check("register", IP);

        assertThatThrownBy(() -> at(T0.plusSeconds(300)).check("register", IP))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(600));
        assertThat(rows.count()).isEqualTo(3);
        // Cuando el más viejo sale de la ventana (exactamente a los 15 min ya no cuenta), vuelve a pasar.
        assertThatCode(() -> at(T0.plus(WINDOW)).check("register", IP)).doesNotThrowAnyException();
    }

    @Test
    void eachActionAndEachIpCountSeparately() {
        for (int i = 0; i < 3; i++) {
            at(T0).check("register", IP);
        }

        assertThatCode(() -> at(T0).check("password-forgot", IP)).doesNotThrowAnyException();
        assertThatCode(() -> at(T0).check("register", "198.51.100.1")).doesNotThrowAnyException();
    }

    @Test
    void aWindowLongerThanTheLoginOneIsRefused() {
        // La poda de LoginThrottle borraría pedidos que todavía cuentan.
        assertThatThrownBy(() -> new RequestThrottle(rows, new RequestThrottleProperties(WINDOW.plusSeconds(1), 3),
                LOGIN, Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
    }
}
