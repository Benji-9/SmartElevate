package com.smartelevate.elevator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartelevate.elevator.dto.QrTokenResponse;
import com.smartelevate.elevator.model.Kiosk;
import com.smartelevate.elevator.repository.KioskRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QrTokenServiceTest {

    private static final String SECRET = "una-clave-de-prueba-de-al-menos-32-bytes";
    // Inicio exacto de un ciclo de 30 s.
    private static final Instant T0 = Instant.parse("2026-10-14T15:40:00Z");

    private final KioskRepository kiosks = mock(KioskRepository.class);
    private Kiosk kiosk;

    @BeforeEach
    void setUp() {
        kiosk = new Kiosk("33", "hash", false, T0);
        when(kiosks.findById(kiosk.getId())).thenReturn(Optional.of(kiosk));
    }

    private QrTokenService at(Instant now) {
        return at(now, SECRET);
    }

    private QrTokenService at(Instant now, String secret) {
        return new QrTokenService(new KioskProperties(secret, Duration.ofSeconds(30), false, 10, Duration.ofMinutes(1)),
                kiosks, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void validTokenCarriesTheKioskElevatorAndId() {
        QrTokenResponse issued = at(T0.plusSeconds(10)).issue(kiosk);

        assertThat(issued.expiresAt()).isEqualTo(T0.plusSeconds(30));
        var claims = at(T0.plusSeconds(12)).validate(issued.token());
        assertThat(claims.elevatorId()).isEqualTo("33");
        assertThat(claims.kioskId()).isEqualTo(kiosk.getId());
        assertThat(claims.simulated()).isFalse();
    }

    @Test
    void sameCycleGivesTheSameToken() {
        assertThat(at(T0.plusSeconds(1)).issue(kiosk)).isEqualTo(at(T0.plusSeconds(29)).issue(kiosk));
    }

    @Test
    void tokenOfThePreviousCycleIsAccepted() {
        String token = at(T0.plusSeconds(29)).issue(kiosk).token();

        // Ya rotó (vence a T0+30) pero sigue dentro de la tolerancia de un ciclo.
        assertThat(at(T0.plusSeconds(30)).validate(token).elevatorId()).isEqualTo("33");
        assertThat(at(T0.plusSeconds(59)).validate(token).elevatorId()).isEqualTo("33");
    }

    @Test
    void tokenOlderThanOneCycleIsRejected() {
        String token = at(T0.plusSeconds(29)).issue(kiosk).token();

        assertThatThrownBy(() -> at(T0.plusSeconds(60)).validate(token)).isInstanceOf(InvalidQrTokenException.class);
    }

    @Test
    void alteredSignatureIsRejected() {
        String token = at(T0).issue(kiosk).token();
        String[] parts = token.split("\\.");
        byte[] sig = Base64.getUrlDecoder().decode(parts[1]);
        sig[0] ^= 1;
        String altered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);

        assertThatThrownBy(() -> at(T0).validate(altered)).isInstanceOf(InvalidQrTokenException.class);
    }

    @Test
    void alteredPayloadIsRejected() {
        String token = at(T0).issue(kiosk).token();
        String sig = token.split("\\.")[1];
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("34|" + kiosk.getId() + "|" + T0.plusSeconds(30).getEpochSecond()).getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> at(T0).validate(forged + "." + sig)).isInstanceOf(InvalidQrTokenException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = at(T0, "otra-clave-distinta-de-al-menos-32-bytes").issue(kiosk).token();

        assertThatThrownBy(() -> at(T0).validate(token)).isInstanceOf(InvalidQrTokenException.class);
    }

    @Test
    void malformedTokensAreRejected() {
        for (String bad : new String[] {null, "", "abc", "a.b.c", "!!!.???", "YWJj.YWJj"}) {
            assertThatThrownBy(() -> at(T0).validate(bad)).isInstanceOf(InvalidQrTokenException.class);
        }
    }

    @Test
    void tokenOfARevokedKioskIsRejectedRightAway() {
        String token = at(T0).issue(kiosk).token();
        kiosk.revoke(T0.plusSeconds(1));

        assertThatThrownBy(() -> at(T0.plusSeconds(2)).validate(token)).isInstanceOf(InvalidQrTokenException.class);
    }

    @Test
    void simulatedKioskIsReportedFromTheDatabase() {
        Kiosk simulated = new Kiosk("05", "hash2", true, T0);
        when(kiosks.findById(simulated.getId())).thenReturn(Optional.of(simulated));

        assertThat(at(T0).validate(at(T0).issue(simulated).token()).simulated()).isTrue();
    }

    @Test
    void shortSecretPreventsStartup() {
        assertThatThrownBy(() -> at(T0, "corta")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_KIOSK_QR_SECRET");
    }
}
