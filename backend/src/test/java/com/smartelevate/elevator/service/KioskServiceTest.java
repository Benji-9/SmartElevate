package com.smartelevate.elevator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartelevate.common.security.CurrentKiosk;
import com.smartelevate.elevator.dto.CreateKioskRequest;
import com.smartelevate.elevator.model.Kiosk;
import com.smartelevate.elevator.repository.ElevatorRepository;
import com.smartelevate.elevator.repository.KioskRepository;
import com.smartelevate.priority.model.PriorityAudit;
import com.smartelevate.priority.repository.PriorityAuditRepository;
import com.smartelevate.user.service.AuthService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KioskServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-14T15:40:00Z");
    private static final UUID ADMIN = UUID.randomUUID();

    private final KioskRepository kiosks = mock(KioskRepository.class);
    private final ElevatorRepository elevators = mock(ElevatorRepository.class);
    private final PriorityAuditRepository audit = mock(PriorityAuditRepository.class);
    private final MutableClock clock = new MutableClock(T0);

    @BeforeEach
    void setUp() {
        when(elevators.existsById("33")).thenReturn(true);
        when(kiosks.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private KioskService service(boolean simulatorEnabled, int maxRequests) {
        var props = new KioskProperties("una-clave-de-prueba-de-al-menos-32-bytes", Duration.ofSeconds(30),
                simulatorEnabled, maxRequests, Duration.ofMinutes(1));
        return new KioskService(kiosks, elevators, audit, new QrTokenService(props, kiosks, clock), props, clock);
    }

    @Test
    void createStoresOnlyTheHashAndReturnsTheSecretOnce() {
        var created = service(false, 10).create(ADMIN, new CreateKioskRequest("33", null));

        var saved = ArgumentCaptor.forClass(Kiosk.class);
        verify(kiosks).save(saved.capture());
        assertThat(created.secret()).hasSizeGreaterThanOrEqualTo(43); // 32 bytes en Base64URL
        assertThat(saved.getValue().getSecretHash()).isEqualTo(AuthService.hash(created.secret()))
                .isNotEqualTo(created.secret());
        assertThat(saved.getValue().isSimulated()).isFalse();

        var audited = ArgumentCaptor.forClass(PriorityAudit.class);
        verify(audit).save(audited.capture());
        assertThat(audited.getValue().getTargetKioskId()).isEqualTo(created.id());
        assertThat(audited.getValue().getTargetUserId()).isNull();
        assertThat(audited.getValue().getActorId()).isEqualTo(ADMIN);
        assertThat(audited.getValue().getAction()).isEqualTo("KIOSK_CREATED");
    }

    @Test
    void secretsAreRandom() {
        KioskService service = service(false, 10);
        assertThat(service.create(ADMIN, new CreateKioskRequest("33", false)).secret())
                .isNotEqualTo(service.create(ADMIN, new CreateKioskRequest("33", false)).secret());
    }

    @Test
    void simulatedKioskIsRejectedWithTheFlagOff() {
        assertThatThrownBy(() -> service(false, 10).create(ADMIN, new CreateKioskRequest("33", true)))
                .isInstanceOfSatisfying(KioskException.class,
                        e -> assertThat(e.getReason()).isEqualTo(KioskException.Reason.SIMULATOR_DISABLED));
        verify(kiosks, never()).save(any());
        verify(audit, never()).save(any());
    }

    @Test
    void simulatedKioskIsCreatedWithTheFlagOn() {
        assertThat(service(true, 10).create(ADMIN, new CreateKioskRequest("33", true)).simulated()).isTrue();
    }

    @Test
    void unknownElevatorIsRejected() {
        assertThatThrownBy(() -> service(false, 10).create(ADMIN, new CreateKioskRequest("99", false)))
                .isInstanceOfSatisfying(KioskException.class,
                        e -> assertThat(e.getReason()).isEqualTo(KioskException.Reason.ELEVATOR_NOT_FOUND));
    }

    @Test
    void revokedKioskNoLongerAuthenticates() {
        Kiosk kiosk = new Kiosk("33", AuthService.hash("s3cr3t"), false, T0);
        when(kiosks.findBySecretHash(AuthService.hash("s3cr3t"))).thenReturn(Optional.of(kiosk));
        when(kiosks.findById(kiosk.getId())).thenReturn(Optional.of(kiosk));
        KioskService service = service(false, 10);
        assertThat(service.authenticate("s3cr3t")).contains(new CurrentKiosk(kiosk.getId(), "33"));

        service.revoke(ADMIN, kiosk.getId());

        assertThat(kiosk.getRevokedAt()).isEqualTo(T0);
        assertThat(service.authenticate("s3cr3t")).isEmpty();
        verify(audit).save(any());
        // Revocar de nuevo no vuelve a auditar.
        service.revoke(ADMIN, kiosk.getId());
        verify(audit).save(any());
    }

    @Test
    void tokenTakesTheElevatorFromTheKioskAndUpdatesLastSeen() {
        Kiosk kiosk = new Kiosk("33", "hash", false, T0);
        when(kiosks.findById(kiosk.getId())).thenReturn(Optional.of(kiosk));
        KioskService service = service(false, 10);
        clock.now = T0.plusSeconds(5);

        var token = service.issueQrToken(new CurrentKiosk(kiosk.getId(), "33"));

        assertThat(kiosk.getLastSeenAt()).isEqualTo(T0.plusSeconds(5));
        var props = new KioskProperties("una-clave-de-prueba-de-al-menos-32-bytes", Duration.ofSeconds(30),
                false, 10, Duration.ofMinutes(1));
        assertThat(new QrTokenService(props, kiosks, clock).validate(token.token()).elevatorId()).isEqualTo("33");
    }

    @Test
    void rateLimitPerKioskWithRetryAfterAndResetAfterTheWindow() {
        Kiosk kiosk = new Kiosk("33", "hash", false, T0);
        Kiosk other = new Kiosk("33", "hash2", false, T0);
        when(kiosks.findById(kiosk.getId())).thenReturn(Optional.of(kiosk));
        when(kiosks.findById(other.getId())).thenReturn(Optional.of(other));
        KioskService service = service(false, 3);
        CurrentKiosk current = new CurrentKiosk(kiosk.getId(), "33");

        for (int i = 0; i < 3; i++) {
            service.issueQrToken(current);
        }
        clock.now = T0.plusSeconds(20);
        assertThatThrownBy(() -> service.issueQrToken(current))
                .isInstanceOfSatisfying(KioskRateLimitedException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(40));
        // El tope es por kiosco: otro sigue funcionando.
        service.issueQrToken(new CurrentKiosk(other.getId(), "33"));

        clock.now = T0.plusSeconds(60);
        service.issueQrToken(current);
    }

    /** Reloj que el test puede adelantar; el service lo lee en cada llamada. */
    static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
