package com.smartelevate.turn.model;

/** Estados de una reserva. USED y NO_SHOW conservan el lugar (V16); las cancelaciones lo liberan. */
public enum ReservationStatus {
    ACTIVE,
    /** Cancelada hasta {@code cancellation_deadline_before} antes de la salida: sin falta. */
    CANCELLED,
    /** Cancelada pasado ese límite: cuenta como falta. */
    CANCELLED_LATE,
    /** Check-in a tiempo, en el ascensor de la reserva o en otro (#230). */
    USED,
    /**
     * Sin check-in a tiempo: cuenta como falta. Lo marca el job a {@code no_show_after} de la salida (#231), o un
     * escaneo fuera de hora (LATE) si llega antes que el job.
     */
    NO_SHOW
}
