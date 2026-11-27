package com.smartelevate.turn.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartelevate.elevator.model.Building;
import com.smartelevate.elevator.model.Core;
import com.smartelevate.elevator.model.Elevator;
import com.smartelevate.elevator.model.ElevatorUsage;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.service.ZoneOfferResolver.Candidate;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Resolución de la oferta de una hora por zonas (ADR 0025), con las zonas semilla de V23. */
class ZoneOfferResolverTest {

    private static final Core L1 = new Core("L1", new Building("LIMA", "Lima", -4, 10), "Lima 1");
    private static final Core L3 = new Core("L3", L1.getBuilding(), "Lima 3");
    private static final Instant AT = Instant.parse("2026-10-13T16:10:00Z");
    private static final int MAX = 2;

    private static Set<Integer> range(int from, int to) {
        return IntStream.rangeClosed(from, to).boxed().collect(Collectors.toSet());
    }

    private static Set<Integer> union(Set<Integer> a, Set<Integer> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).collect(Collectors.toSet());
    }

    private static Set<Integer> zone(String code) {
        return switch (code) {
            case "01", "02" -> union(range(-3, 0), range(5, 6));
            case "03", "04" -> Set.of(7);
            case "10", "11" -> union(range(-3, 0), range(5, 6));
            case "12", "13" -> range(7, 8);
            default -> range(9, 10); // 14, 15
        };
    }

    private static Candidate candidate(String code, int remaining, Integer... destinations) {
        Core core = code.compareTo("10") < 0 ? L1 : L3;
        Elevator e = new Elevator(code, core, ElevatorUsage.COMMON, true, range(-3, 10), zone(code));
        return new Candidate(new Departure(e, AT, 10), remaining, destinations.length, List.of(destinations));
    }

    private static String pick(List<Candidate> sameHour, int destination, boolean open) {
        return ZoneOfferResolver.pick(sameHour, destination, open, MAX).departure().getElevator().getCode();
    }

    @Test
    void prefersSeedZoneWhenEmpty() {
        assertThat(pick(List.of(candidate("01", 8), candidate("02", 8), candidate("03", 8), candidate("04", 8)),
                7, false)).isEqualTo("03");
    }

    @Test
    void joinsDepartureWithinTwoFloors() {
        assertThat(pick(List.of(candidate("01", 7, 5), candidate("02", 8), candidate("03", 8), candidate("04", 8)),
                7, false)).isEqualTo("01");
    }

    @Test
    void doesNotJoinDepartureThreeFloorsAway() {
        // 7 − 4 = 3 > 2: el 01 no se suma y gana la zona del 7.
        assertThat(pick(List.of(candidate("01", 7, 4), candidate("03", 8)), 7, false)).isEqualTo("03");
    }

    @Test
    void opensZoneElevatorWhenBookedIsFar() {
        List<Candidate> l3 = List.of(candidate("15", 8), candidate("14", 8), candidate("13", 8), candidate("12", 8),
                candidate("11", 8), candidate("10", 7, 5));
        assertThat(pick(l3, 9, false)).isEqualTo("14");
    }

    @Test
    void ignoresDepartureWithAnyFarDestination() {
        List<Candidate> l3 = List.of(candidate("10", 8), candidate("11", 8), candidate("12", 6, 5, 7),
                candidate("13", 8), candidate("14", 8), candidate("15", 8));
        assertThat(pick(l3, 9, false)).isEqualTo("14");
    }

    @Test
    void usesAnyEmptyWhenZoneIsTaken() {
        assertThat(pick(List.of(candidate("04", 7, -3), candidate("03", 7, -3), candidate("02", 8),
                candidate("01", 8)), 7, false)).isEqualTo("01");
    }

    @Test
    void picksSmallestMaxDistanceWhenAllFar() {
        assertThat(pick(List.of(candidate("01", 7, -3), candidate("02", 7, 0), candidate("03", 6, -3, 0),
                candidate("04", 7, -1)), 7, false)).isEqualTo("02");
    }

    @Test
    void tieBreaksByMostOccupied() {
        // Afinidad: misma distancia mínima, gana la más ocupada.
        assertThat(pick(List.of(candidate("01", 7, 6), candidate("02", 6, 6, 6)), 7, false)).isEqualTo("02");
        // Todos lejos: misma distancia máxima, gana la más ocupada.
        assertThat(pick(List.of(candidate("01", 7, 0), candidate("02", 6, 0, 0)), 7, false)).isEqualTo("02");
        // Mismo todo: menor código.
        assertThat(pick(List.of(candidate("02", 7, 6), candidate("01", 7, 6)), 7, false)).isEqualTo("01");
    }

    @Test
    void opensToAnyWhenOpen() {
        assertThat(pick(List.of(candidate("03", 7, 7), candidate("02", 8), candidate("01", 8)), 7, true))
                .isEqualTo("01");
        assertThat(pick(List.of(candidate("03", 9, 7), candidate("01", 8)), 7, true)).isEqualTo("03");
    }

    @Test
    void skipsFullDepartures() {
        assertThat(pick(List.of(candidate("01", 0, 7), candidate("03", 0), candidate("04", 8)), 7, false))
                .isEqualTo("04");
    }

    @Test
    void returnsFullOfferWhenAllFull() {
        assertThat(pick(List.of(candidate("04", 0), candidate("03", 0, 7), candidate("02", 0)), 7, false))
                .isEqualTo("02");
    }
}
