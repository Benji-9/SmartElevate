package com.smartelevate.elevator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Pantalla que muestra el QR de un ascensor (ADR 0020). Se autentica con un secreto propio del que solo se guarda
 * el hash (ver V15); el ascensor del QR sale siempre de acá.
 */
@Entity
@Table(name = "kiosk")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Kiosk {

    @Id
    private UUID id;

    @Column(name = "elevator_code")
    private String elevatorId;

    @Column(name = "secret_hash")
    private String secretHash;

    private boolean simulated;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at")
    private Instant createdAt;

    public Kiosk(String elevatorId, String secretHash, boolean simulated, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.elevatorId = elevatorId;
        this.secretHash = secretHash;
        this.simulated = simulated;
        this.createdAt = createdAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke(Instant now) {
        this.revokedAt = now;
    }

    public void seen(Instant now) {
        this.lastSeenAt = now;
    }
}
