package com.smartelevate.user.model;

/** Rol de una cuenta (ADR 0009). Todo usuario nace {@code USER}; los demás los asigna un ADMIN. */
public enum Role {
    USER,
    /** Revisa los certificados de prioridad. */
    REVIEWER,
    ADMIN
}
