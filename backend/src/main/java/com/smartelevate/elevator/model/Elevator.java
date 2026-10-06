package com.smartelevate.elevator.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Ascensor, identificado por su código de cartel (01, 33...). Sirve su propia lista de pisos, no un rango. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Elevator {

    @Id
    private String code;

    @ManyToOne(optional = false)
    @JoinColumn(name = "core_code")
    private Core core;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type")
    private ElevatorUsage usage;

    private boolean active;

    // Eager: son pocos pisos y así se pueden leer fuera de una transacción.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "elevator_floor", joinColumns = @JoinColumn(name = "elevator_code"))
    @Column(name = "floor_number")
    private Set<Integer> floors;
}
