package com.smartelevate.elevator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Paso entre dos núcleos, válido en los pisos {@code fromFloor} a {@code toFloor}: una arista del grafo.
 * El tiempo de caminata y la accesibilidad pueden ser null mientras estén a medir / a confirmar (#22).
 */
@Entity
@Table(name = "core_connection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CoreConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "core_a_code")
    private Core coreA;

    @ManyToOne(optional = false)
    @JoinColumn(name = "core_b_code")
    private Core coreB;

    @Column(name = "from_floor")
    private int fromFloor;

    @Column(name = "to_floor")
    private int toFloor;

    @Column(name = "walking_time_seconds")
    private Integer walkingTimeSeconds;

    private Boolean accessible;
}
