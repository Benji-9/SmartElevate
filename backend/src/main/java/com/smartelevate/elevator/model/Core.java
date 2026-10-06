package com.smartelevate.elevator.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Núcleo: grupo de ascensores de un edificio y nodo del grafo de conexiones. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Core {

    @Id
    private String code;

    @ManyToOne(optional = false)
    @JoinColumn(name = "building_code")
    private Building building;

    /** Nombre visible, p. ej. "Independencia 1". */
    private String name;
}
