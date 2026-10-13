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

/** Registro de un cambio de rol o prioridad: quién, a quién, qué y cuándo. Sin datos sensibles. */
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
}
