package com.smartelevate.turn.model;

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

/** Suspensión por faltas (V17): rige de startsAt a endsAt; la disparó la falta de triggerFaultAt. */
@Entity
@Table(name = "no_show_suspension")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoShowSuspension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "trigger_fault_at")
    private Instant triggerFaultAt;

    public NoShowSuspension(UUID userId, Instant triggerFaultAt, Instant endsAt) {
        this.userId = userId;
        this.startsAt = triggerFaultAt;
        this.endsAt = endsAt;
        this.triggerFaultAt = triggerFaultAt;
    }
}
