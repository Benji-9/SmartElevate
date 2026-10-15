package com.smartelevate.elevator.service;

import com.smartelevate.common.security.CurrentKiosk;
import com.smartelevate.elevator.dto.CreateKioskRequest;
import com.smartelevate.elevator.dto.CreatedKioskResponse;
import com.smartelevate.elevator.dto.KioskListResponse;
import com.smartelevate.elevator.dto.KioskResponse;
import com.smartelevate.elevator.dto.QrTokenResponse;
import com.smartelevate.elevator.model.Kiosk;
import com.smartelevate.elevator.repository.ElevatorRepository;
import com.smartelevate.elevator.repository.KioskRepository;
import com.smartelevate.priority.model.PriorityAudit;
import com.smartelevate.priority.repository.PriorityAuditRepository;
import com.smartelevate.user.service.AuthService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kioscos del QR (ADR 0020): alta y revocación (ADMIN, auditadas en {@code priority_audit}), autenticación por
 * credencial de dispositivo y entrega del token del QR. El secreto se genera con CSPRNG, se devuelve una sola vez y
 * en la base queda solo su SHA-256, como con los refresh tokens.
 */
@Service
@RequiredArgsConstructor
public class KioskService {

    /** 256 bits: con esta entropía no hace falta un hash lento ni frenar intentos de adivinarlo. */
    private static final int SECRET_BYTES = 32;

    private final KioskRepository kiosks;
    private final ElevatorRepository elevators;
    private final PriorityAuditRepository audit;
    private final QrTokenService tokens;
    private final KioskProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    // ponytail: rate limit en memoria, por instancia (Render corre una sola). Con N instancias el tope real es N
    // veces el configurado; si se escala, pasarlo a la base o a Redis. Una entrada por kiosco: no crece sin límite.
    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();

    private record Window(Instant start, int count) {
    }

    @Transactional
    public CreatedKioskResponse create(UUID actorId, CreateKioskRequest request) {
        boolean simulated = Boolean.TRUE.equals(request.simulated());
        if (simulated && !props.simulatorEnabled()) {
            throw new KioskException(KioskException.Reason.SIMULATOR_DISABLED,
                    "El simulador está deshabilitado: no se pueden crear kioscos simulados");
        }
        if (!elevators.existsById(request.elevatorId())) {
            throw new KioskException(KioskException.Reason.ELEVATOR_NOT_FOUND, "Ascensor inexistente");
        }
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Kiosk kiosk = kiosks.save(new Kiosk(request.elevatorId(), AuthService.hash(secret), simulated, now));
        audit.save(PriorityAudit.forKiosk(actorId, kiosk.getId(), "KIOSK_CREATED",
                kiosk.getElevatorId() + (simulated ? " simulated" : ""), now));
        return new CreatedKioskResponse(kiosk.getId(), kiosk.getElevatorId(), simulated, now, secret);
    }

    @Transactional(readOnly = true)
    public KioskListResponse list() {
        return new KioskListResponse(props.simulatorEnabled(), kiosks.findAllByOrderByCreatedAtAsc().stream()
                .map(k -> new KioskResponse(k.getId(), k.getElevatorId(), k.isSimulated(), k.getCreatedAt(),
                        k.getLastSeenAt(), k.getRevokedAt()))
                .toList());
    }

    /** Revoca el kiosco: deja de autenticarse y sus QR dejan de validar en el acto. Revocar dos veces no hace nada. */
    @Transactional
    public void revoke(UUID actorId, UUID kioskId) {
        Kiosk kiosk = kiosks.findById(kioskId)
                .orElseThrow(() -> new KioskException(KioskException.Reason.KIOSK_NOT_FOUND, "Kiosco inexistente"));
        if (kiosk.isRevoked()) {
            return;
        }
        Instant now = clock.instant();
        kiosk.revoke(now);
        kiosks.save(kiosk);
        audit.save(PriorityAudit.forKiosk(actorId, kioskId, "KIOSK_REVOKED", kiosk.getElevatorId(), now));
    }

    /** El kiosco activo dueño del secreto, si existe. Lo usa el filtro de seguridad de {@code /api/kiosks/**}. */
    @Transactional(readOnly = true)
    public Optional<CurrentKiosk> authenticate(String secret) {
        return kiosks.findBySecretHash(AuthService.hash(secret))
                .filter(k -> !k.isRevoked())
                .map(k -> new CurrentKiosk(k.getId(), k.getElevatorId()));
    }

    /**
     * Token del QR para el kiosco autenticado: el ascensor sale de la credencial. Registra {@code lastSeenAt}.
     *
     * @throws KioskRateLimitedException si superó {@code app.kiosk.rate-limit-max-requests} en la ventana
     */
    @Transactional
    public QrTokenResponse issueQrToken(CurrentKiosk current) {
        Instant now = clock.instant();
        checkRateLimit(current.id(), now);
        Kiosk kiosk = kiosks.findById(current.id()).filter(k -> !k.isRevoked())
                .orElseThrow(() -> new KioskException(KioskException.Reason.KIOSK_NOT_FOUND, "Kiosco inexistente"));
        kiosk.seen(now);
        kiosks.save(kiosk);
        return tokens.issue(kiosk);
    }

    // Ventana fija por kiosco: alcanza para frenar un kiosco (o un secreto filtrado) que pide sin parar.
    private void checkRateLimit(UUID kioskId, Instant now) {
        Duration length = props.rateLimitWindow();
        Window w = windows.compute(kioskId, (id, cur) -> cur == null || !now.isBefore(cur.start().plus(length))
                ? new Window(now, 1) : new Window(cur.start(), cur.count() + 1));
        if (w.count() > props.rateLimitMaxRequests()) {
            throw new KioskRateLimitedException(Duration.between(now, w.start().plus(length)));
        }
    }
}
