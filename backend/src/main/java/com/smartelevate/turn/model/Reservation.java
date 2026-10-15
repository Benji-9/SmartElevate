package com.smartelevate.turn.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Lugar de un usuario en una salida. El lugar (seatNumber) es único por salida mientras la reserva está activa. */
@Entity
@Table(name = "reservation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private UUID userId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "departure_id")
    private Departure departure;

    @Column(name = "seat_number")
    private Integer seatNumber;

    @Column(name = "origin_floor")
    private int originFloor;

    @Column(name = "destination_floor")
    private int destinationFloor;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    /** El usuario tenía una prioridad vigente al reservar: cuenta para los lugares reservados a prioritarios. */
    @Column(name = "priority_user")
    private boolean priorityUser;

    /** Cuándo se reclamó el recordatorio por email (V20); null = todavía no. */
    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    public Reservation(UUID userId, Departure departure, int seatNumber, int originFloor, int destinationFloor,
                       Instant createdAt) {
        this(userId, departure, seatNumber, originFloor, destinationFloor, createdAt, false);
    }

    public Reservation(UUID userId, Departure departure, int seatNumber, int originFloor, int destinationFloor,
                       Instant createdAt, boolean priorityUser) {
        this.userId = userId;
        this.departure = departure;
        this.seatNumber = seatNumber;
        this.originFloor = originFloor;
        this.destinationFloor = destinationFloor;
        this.status = ReservationStatus.ACTIVE;
        this.createdAt = createdAt;
        this.priorityUser = priorityUser;
    }

    /**
     * Cancela y libera el lugar (seatNumber NULL, ver V7 y V11). Sin falta hasta {@code deadline} antes de la salida
     * inclusive; después, CANCELLED_LATE. Idempotente: si ya no está activa, no cambia nada.
     */
    public void cancel(Instant now, Duration deadline) {
        if (status != ReservationStatus.ACTIVE) {
            return;
        }
        status = cancelIsLate(now, deadline) ? ReservationStatus.CANCELLED_LATE : ReservationStatus.CANCELLED;
        seatNumber = null;
    }

    /** Cancelar en {@code now} cuenta como falta: pasó {@code deadline} antes de la salida. */
    public boolean cancelIsLate(Instant now, Duration deadline) {
        return now.isAfter(departure.getDepartureAt().minus(deadline));
    }

    /** Check-in a tiempo (#230). Conserva el lugar. */
    public void markUsed() {
        status = ReservationStatus.USED;
    }

    /** Falta por no hacer check-in a tiempo (#230 fuera de hora, #231 el job). Conserva el lugar. */
    public void markNoShow() {
        status = ReservationStatus.NO_SHOW;
    }
}
