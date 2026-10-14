package com.smartelevate.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.smartelevate.common.config.ConfigService;
import com.smartelevate.user.model.LoginFailure;
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

/** Contra la base real (H2 con las migraciones): las consultas y los borrados son parte de la regla. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LoginThrottleTest {

    private static final Instant T0 = Instant.parse("2026-10-14T13:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int PER_ACCOUNT = 3;
    private static final int PER_IP = 5;
    private static final String ANA = "ana@uade.edu.ar";
    private static final String IP = "203.0.113.7";

    @Autowired
    private LoginFailureRepository failures;

    private final ConfigService config = mock(ConfigService.class);

    @BeforeEach
    void setUp() {
        // Los tests de integración comparten la base en memoria y pueden dejar fallos suyos (se revierte al final).
        failures.deleteAll();
        given(config.loginFailureWindow()).willReturn(WINDOW);
        given(config.loginMaxFailuresPerAccount()).willReturn(PER_ACCOUNT);
        given(config.loginMaxFailuresPerIp()).willReturn(PER_IP);
    }

    private LoginThrottle at(Instant now) {
        return new LoginThrottle(failures, config, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void fail(int times, String account, String ip, Instant at) {
        for (int i = 0; i < times; i++) {
            at(at).recordFailure(account, ip);
        }
    }

    private long retryAfter(Instant now, String account, String ip) {
        try {
            at(now).check(account, ip);
        } catch (TooManyLoginAttemptsException e) {
            return e.getRetryAfterSeconds();
        }
        return 0;
    }

    @Test
    void theAccountIsBlockedExactlyAtTheLimit() {
        fail(PER_ACCOUNT - 1, ANA, IP, T0);
        assertThatCode(() -> at(T0).check(ANA, IP)).doesNotThrowAnyException();

        fail(1, ANA, IP, T0);
        assertThatThrownBy(() -> at(T0).check(ANA, IP)).isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void retryAfterIsWhenTheOldestFailureLeavesTheSlidingWindow() {
        fail(1, ANA, IP, T0);
        fail(PER_ACCOUNT - 1, ANA, IP, T0.plus(Duration.ofMinutes(5)));

        assertThat(retryAfter(T0.plus(Duration.ofMinutes(5)), ANA, IP)).isEqualTo(Duration.ofMinutes(10).toSeconds());
        assertThat(retryAfter(T0.plus(WINDOW).minusSeconds(1), ANA, IP)).isEqualTo(1);
        // Al cumplirse la ventana el primer fallo deja de contar: hay un intento más, no un desbloqueo total.
        assertThat(retryAfter(T0.plus(WINDOW), ANA, IP)).isZero();
        fail(1, ANA, IP, T0.plus(WINDOW));
        assertThat(retryAfter(T0.plus(WINDOW), ANA, IP)).isEqualTo(Duration.ofMinutes(5).toSeconds());
    }

    @Test
    void retryAfterRoundsUpToWholeSeconds() {
        fail(PER_ACCOUNT, ANA, IP, T0);

        assertThat(retryAfter(T0.plusMillis(1), ANA, IP)).isEqualTo(WINDOW.toSeconds());
    }

    @Test
    void anotherAccountIsNotAffectedButTheSameIpIsLimitedAcrossAccounts() {
        fail(PER_ACCOUNT, ANA, IP, T0);
        assertThatCode(() -> at(T0).check("beto@uade.edu.ar", "198.51.100.1")).doesNotThrowAnyException();

        fail(PER_IP - PER_ACCOUNT, "beto@uade.edu.ar", IP, T0);
        assertThatThrownBy(() -> at(T0).check("carla@uade.edu.ar", IP))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        // Y el límite de la cuenta vale aunque se cambie de IP.
        assertThatThrownBy(() -> at(T0).check(ANA, "198.51.100.1"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void whenBothAreBlockedTheLongestWaitWins() {
        fail(PER_ACCOUNT, ANA, "198.51.100.1", T0);
        fail(PER_IP, "beto@uade.edu.ar", IP, T0.plus(Duration.ofMinutes(1)));

        // Cuenta: libera a los 13 min; IP: a los 14.
        assertThat(retryAfter(T0.plus(Duration.ofMinutes(2)), ANA, IP)).isEqualTo(Duration.ofMinutes(14).toSeconds());
    }

    @Test
    void clearingTheAccountKeepsTheIpFailures() {
        fail(PER_ACCOUNT, ANA, IP, T0);

        at(T0).clearAccount(ANA);

        assertThatCode(() -> at(T0).check(ANA, "198.51.100.1")).doesNotThrowAnyException();
        // La IP sigue con sus 3 fallos: con 2 más, de otra cuenta, llega al tope.
        fail(PER_IP - PER_ACCOUNT, "beto@uade.edu.ar", IP, T0);
        assertThatThrownBy(() -> at(T0).check("carla@uade.edu.ar", IP))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void failuresOutsideTheWindowArePrunedAndOnlyHashesAreStored() {
        fail(2, ANA, IP, T0);
        fail(1, "beto@uade.edu.ar", IP, T0.plus(WINDOW));

        // Los 2 fallos de Ana (cuenta + IP = 4 filas) salieron de la ventana y se borraron.
        assertThat(failures.findAll()).hasSize(2)
                .extracting(LoginFailure::getSubject)
                .allSatisfy(subject -> assertThat(subject).hasSize(64).doesNotContain("@").doesNotContain(IP));
    }
}
