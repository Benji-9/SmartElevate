package com.smartelevate.elevator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Edificio del campus con su rango de pisos (el 0 es planta baja). */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Building {

    @Id
    private String code;

    private String name;

    @Column(name = "min_floor")
    private int minFloor;

    @Column(name = "max_floor")
    private int maxFloor;
}
