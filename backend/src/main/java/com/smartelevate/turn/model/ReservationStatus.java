package com.smartelevate.turn.model;

/** Cumplida y no-show llegan con el check-in. */
public enum ReservationStatus {
    ACTIVE,
    /** Cancelada hasta {@code cancellation_deadline_before} antes de la salida: sin falta. */
    CANCELLED,
    /** Cancelada pasado ese límite: cuenta como falta (el registro de no-show llega con el check-in). */
    CANCELLED_LATE
}
