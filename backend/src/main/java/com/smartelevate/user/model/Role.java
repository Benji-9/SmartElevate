package com.smartelevate.user.model;

/** Rol de una cuenta (ADR 0009). Todo usuario nace {@code USER}; los demás los asigna un ADMIN. */
public enum Role {
    USER,
    /**
     * Sin función hoy: iba a revisar los certificados de prioridad, pero los maneja UADE a través de OFALU
     * (ADR 0024). Se conserva en la base y en la API hasta decidir si se quita.
     */
    REVIEWER,
    ADMIN
}
