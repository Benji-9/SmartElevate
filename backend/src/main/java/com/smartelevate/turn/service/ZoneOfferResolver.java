package com.smartelevate.turn.service;

import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;

import com.smartelevate.turn.model.Departure;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Elige qué salida de una hora se le ofrece a quien va a {@code destination} (ADR 0025, asignacion.md#zonas-de-pisos).
 * Las candidatas ya vienen filtradas (núcleo, ascensores activos que puede usar y que paran en origen y destino):
 * <ol>
 *   <li>Sin lugar no cuentan; si ninguna tiene, se devuelve la de menor código (se ve "Completo").</li>
 *   <li>Abierta ({@code zone_open_to_any_before} o ascensores dedicados): la de más lugares libres.</li>
 *   <li>Afinidad: con reservas y todos sus destinos a {@code maxFloors} pisos o menos; la de menor distancia
 *       mínima y, a igual distancia, la más ocupada.</li>
 *   <li>Una vacía cuya zona semilla contiene el destino.</li>
 *   <li>Cualquier vacía.</li>
 *   <li>Todas llevan destinos lejanos: la de menor distancia máxima y, a igual distancia, la más ocupada.</li>
 * </ol>
 * Último desempate en todos los pasos: menor código de ascensor.
 */
final class ZoneOfferResolver {

    /** Una salida de la hora con lo que ve este usuario: lugares que puede tomar, reservas y sus destinos. */
    record Candidate(Departure departure, int remaining, int booked, List<Integer> destinations) {

        String code() {
            return departure.getElevator().getCode();
        }
    }

    private static final Comparator<Candidate> BY_CODE = comparing(Candidate::code);
    private static final Comparator<Candidate> MOST_BOOKED = comparingInt(Candidate::booked).reversed();

    private ZoneOfferResolver() {
    }

    static Candidate pick(List<Candidate> sameHour, int destination, boolean open, int maxFloors) {
        List<Candidate> free = sameHour.stream().filter(c -> c.remaining() > 0).toList();
        if (free.isEmpty()) {
            return sameHour.stream().min(BY_CODE).orElseThrow();
        }
        if (open) {
            return free.stream().min(comparingInt(Candidate::remaining).reversed().thenComparing(BY_CODE))
                    .orElseThrow();
        }
        Optional<Candidate> near = free.stream()
                .filter(c -> c.booked() > 0 && maxDistance(c, destination) <= maxFloors)
                .min(comparingInt((Candidate c) -> minDistance(c, destination))
                        .thenComparing(MOST_BOOKED).thenComparing(BY_CODE));
        if (near.isPresent()) {
            return near.get();
        }
        List<Candidate> empty = free.stream().filter(c -> c.booked() == 0).toList();
        return empty.stream()
                .filter(c -> c.departure().getElevator().getZoneFloors().contains(destination))
                .min(BY_CODE)
                .or(() -> empty.stream().min(BY_CODE))
                .orElseGet(() -> free.stream()
                        .min(comparingInt((Candidate c) -> maxDistance(c, destination))
                                .thenComparing(MOST_BOOKED).thenComparing(BY_CODE))
                        .orElseThrow());
    }

    private static int minDistance(Candidate c, int destination) {
        return c.destinations().stream().mapToInt(d -> Math.abs(d - destination)).min().orElse(0);
    }

    private static int maxDistance(Candidate c, int destination) {
        return c.destinations().stream().mapToInt(d -> Math.abs(d - destination)).max().orElse(0);
    }
}
