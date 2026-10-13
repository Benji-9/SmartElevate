package com.smartelevate.priority.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Decisión sobre la prioridad de un usuario en una categoría. Es vigente si está APPROVED y no venció. */
@Entity
@Table(name = "user_priority")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPriority {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    private PriorityCategory category;

    @Enumerated(EnumType.STRING)
    private PriorityStatus status;

    /** {@code null} = sin vencimiento. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    public UserPriority(UUID userId, PriorityCategory category) {
        this.userId = userId;
        this.category = category;
    }

    public void decide(PriorityStatus status, Instant expiresAt, Instant decidedAt, UUID decidedBy) {
        this.status = status;
        this.expiresAt = expiresAt;
        this.decidedAt = decidedAt;
        this.decidedBy = decidedBy;
    }

    public boolean isCurrent(Instant now) {
        return status == PriorityStatus.APPROVED && (expiresAt == null || expiresAt.isAfter(now));
    }
}
