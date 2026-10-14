package com.smartelevate.turn.model;

/** Resultado de un check-in (check-in-qr.md#resultados). */
public enum CheckInOutcome {
    /** Cumplida: ascensor de la reserva, entre −1 min y +{@code no_show_after}. La reserva queda USED. */
    ON_TIME,
    /** Ascensor distinto, a tiempo. La reserva queda USED; la diferencia alimenta el KPI de distribución. */
    OTHER_ELEVATOR,
    /** Fuera de hora: pasado {@code no_show_after} y dentro de la ventana. Registra que vino, pero es NO_SHOW. */
    LATE
}
