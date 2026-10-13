package com.smartelevate.turn.model;

import com.smartelevate.elevator.model.Elevator;
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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Salida de un ascensor a una hora estimada (UTC). La capacidad se copia de la configuración al generarla. */
@Entity
@Table(name = "departure")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Departure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "elevator_code")
    private Elevator elevator;

    @Column(name = "departure_at")
    private Instant departureAt;

    private int capacity;

    @Enumerated(EnumType.STRING)
    private DepartureStatus status;

    public Departure(Elevator elevator, Instant departureAt, int capacity) {
        this.elevator = elevator;
        this.departureAt = departureAt;
        this.capacity = capacity;
        this.status = DepartureStatus.SCHEDULED;
    }
}
