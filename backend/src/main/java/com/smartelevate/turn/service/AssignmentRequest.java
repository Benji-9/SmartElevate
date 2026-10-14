package com.smartelevate.turn.service;

import com.smartelevate.priority.model.PriorityCategory;
import java.time.Instant;
import java.util.Comparator;

/**
 * Solicitud que compite por un lugar cuando hay más solicitudes que lugares (asignacion.md#orden-de-prioridad).
 * La va a usar la lista de espera, que queda fuera de #117. {@code priority} es la vigente al solicitar
 * ({@link com.smartelevate.priority.service.PriorityService#currentPriority}).
 *
 * <p>Falta el <b>envejecimiento</b>: la regla no dice cuánto sube una solicitud tras N min (un nivel, al tope, o
 * solo saltear el criterio de piso) y N sigue en NULL en {@code app_config}. Se agrega cuando se decida (#113).
 */
public record AssignmentRequest(PriorityCategory priority, int destinationFloor, Instant requestedAt) {

    /** Primero el que va antes: movilidad reducida, docentes, piso de destino más alto, orden de llegada. */
    public static final Comparator<AssignmentRequest> ORDER =
            Comparator.comparing(AssignmentRequest::priority, Comparator.reverseOrder()) // enum: NONE < TEACHER < RM
                    .thenComparing(AssignmentRequest::destinationFloor, Comparator.reverseOrder())
                    .thenComparing(AssignmentRequest::requestedAt);
}
