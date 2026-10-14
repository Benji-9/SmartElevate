package com.smartelevate.turn.model;

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

/** Lectura del QR para una reserva (V16). Una sola por reserva. */
@Entity
@Table(name = "check_in")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id")
    private long reservationId;

    @Enumerated(EnumType.STRING)
    private CheckInOutcome outcome;

    /** Ascensor escaneado (del token firmado), que puede no ser el de la reserva. */
    @Column(name = "elevator_code")
    private String elevatorCode;

    @Enumerated(EnumType.STRING)
    private CheckInSource source;

    @Column(name = "kiosk_id")
    private UUID kioskId;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    public CheckIn(long reservationId, CheckInOutcome outcome, String elevatorCode, CheckInSource source,
                   UUID kioskId, Instant checkedInAt) {
        this.reservationId = reservationId;
        this.outcome = outcome;
        this.elevatorCode = elevatorCode;
        this.source = source;
        this.kioskId = kioskId;
        this.checkedInAt = checkedInAt;
    }
}
