package com.smartelevate.priority.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Registro de una acción de administración: quién, sobre qué, qué y cuándo. Sin datos sensibles. El destino es un
 * usuario (cambios de rol o prioridad) un kiosco (alta y revocación, V15) o un ascensor (fuera de servicio, V21): exactamente uno.
 */
@Entity
@Table(name = "priority_audit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriorityAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "target_kiosk_id")
    private UUID targetKioskId;

    @Column(name = "target_elevator_code")
    private String targetElevatorCode;

    private String action;

    private String detail;

    @Column(name = "created_at")
    private Instant createdAt;

    public PriorityAudit(UUID actorId, UUID targetUserId, String action, String detail, Instant createdAt) {
        this.actorId = actorId;
        this.targetUserId = targetUserId;
        this.action = action;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public static PriorityAudit forElevator(UUID actorId, String elevatorCode, String action, String detail,
                                            Instant createdAt) {
        PriorityAudit audit = new PriorityAudit(actorId, null, action, detail, createdAt);
        audit.targetElevatorCode = elevatorCode;
        return audit;
    }

    public static PriorityAudit forKiosk(UUID actorId, UUID kioskId, String action, String detail, Instant createdAt) {
        PriorityAudit audit = new PriorityAudit(actorId, null, action, detail, createdAt);
        audit.targetKioskId = kioskId;
        return audit;
    }
}
