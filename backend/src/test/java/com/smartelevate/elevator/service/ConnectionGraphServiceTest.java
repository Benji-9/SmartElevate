package com.smartelevate.elevator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartelevate.elevator.model.Building;
import com.smartelevate.elevator.model.Core;
import com.smartelevate.elevator.model.CoreConnection;
import com.smartelevate.elevator.repository.CoreConnectionRepository;
import com.smartelevate.elevator.service.ConnectionGraphService.AlternativeCore;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectionGraphServiceTest {

    private static final Building BUILDING = new Building("X", "Edificio", -4, 11);
    private static final Core IND2 = new Core("IND2", BUILDING);
    private static final Core IND1 = new Core("IND1", BUILDING);
    private static final Core L3 = new Core("L3", BUILDING);
    private static final Core L2 = new Core("L2", BUILDING);
    private static final Core L1 = new Core("L1", BUILDING);

    private static CoreConnection connection(Core a, Core b, int from, int to, Integer seconds, Boolean accessible) {
        return new CoreConnection(null, a, b, from, to, seconds, accessible);
    }

    // Misma forma que el seed de V2 (IND2–IND1 en dos tramos, sin el piso 1), con tiempos y accesibilidad nulos.
    private static final List<CoreConnection> CHAIN = List.of(
            connection(IND2, IND1, -4, 0, null, null),
            connection(IND2, IND1, 2, 10, null, null),
            connection(IND1, L3, -3, 10, null, null),
            connection(L3, L2, -3, 10, null, null),
            connection(L2, L1, -3, 7, null, null));

    private static ConnectionGraphService service(List<CoreConnection> connections) {
        CoreConnectionRepository repository = mock(CoreConnectionRepository.class);
        when(repository.findAll()).thenReturn(connections);
        return new ConnectionGraphService(repository);
    }

    private static List<String> codes(List<AlternativeCore> alternatives) {
        return alternatives.stream().map(AlternativeCore::coreCode).toList();
    }

    @Test
    void reachesCoresOrderedByHops() {
        List<AlternativeCore> result = service(CHAIN).alternatives("L1", 5, false);

        assertThat(codes(result)).containsExactly("L2", "L3", "IND1", "IND2");
        assertThat(result).extracting(AlternativeCore::hops).containsExactly(1, 2, 3, 4);
    }

    @Test
    void isolatedFloorHasNoAlternatives() {
        assertThat(service(CHAIN).alternatives("IND2", 11, false)).isEmpty();
    }

    @Test
    void connectionThatDoesNotCoverTheFloorIsNotCrossed() {
        // IND2 no para en el 1: ningún tramo IND2–IND1 lo cubre.
        assertThat(service(CHAIN).alternatives("IND2", 1, false)).isEmpty();
        assertThat(codes(service(CHAIN).alternatives("IND1", 1, false))).containsExactly("L3", "L2", "L1");
        // L2–L1 llega hasta el 7.
        assertThat(codes(service(CHAIN).alternatives("L3", 8, false))).containsExactly("IND1", "L2", "IND2");
    }

    @Test
    void accessibleOnlySkipsUnconfirmedAndNonAccessibleConnections() {
        List<CoreConnection> connections = List.of(
                connection(L1, L2, 0, 10, 60, false),
                connection(L2, L3, 0, 10, 60, null),
                connection(L1, IND1, 0, 10, 60, true));
        ConnectionGraphService service = service(connections);

        assertThat(service.alternatives("L1", 5, true)).containsExactly(new AlternativeCore("IND1", 1, 60, true));
        assertThat(service.alternatives("L2", 5, true)).isEmpty();
        assertThat(service(CHAIN).alternatives("L1", 5, true)).isEmpty();
    }

    @Test
    void sumsWalkingTimeAndFlagsUnknownSegments() {
        List<CoreConnection> connections = List.of(
                connection(L1, L2, 0, 10, 60, null),
                connection(L2, L3, 0, 10, 90, null),
                connection(L3, IND1, 0, 10, null, null),
                connection(IND1, IND2, 0, 10, 30, null));

        assertThat(service(connections).alternatives("L1", 5, false)).containsExactly(
                new AlternativeCore("L2", 1, 60, true),
                new AlternativeCore("L3", 2, 150, true),
                new AlternativeCore("IND1", 3, 150, false),
                new AlternativeCore("IND2", 4, 180, false));
    }

    @Test
    void unknownOriginHasNoAlternatives() {
        assertThat(service(CHAIN).alternatives("NOPE", 5, false)).isEmpty();
    }
}
