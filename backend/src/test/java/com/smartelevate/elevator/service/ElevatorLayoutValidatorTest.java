package com.smartelevate.elevator.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartelevate.elevator.model.Building;
import com.smartelevate.elevator.model.Core;
import com.smartelevate.elevator.model.CoreConnection;
import com.smartelevate.elevator.model.Elevator;
import com.smartelevate.elevator.model.ElevatorUsage;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ElevatorLayoutValidatorTest {

    // Un edificio de -1 a 5 con dos núcleos: A sirve -1 a 5, B sirve 0 a 3 sin el 1.
    private static final Building BUILDING = new Building("B", "Edificio", -1, 5);
    private static final Core A = new Core("A", BUILDING, "A");
    private static final Core B = new Core("B", BUILDING, "B");

    private static Elevator elevator(String code, Core core, boolean active, Integer... floors) {
        return new Elevator(code, core, ElevatorUsage.COMMON, active, Set.of(floors));
    }

    private static final List<Elevator> ELEVATORS = List.of(
            elevator("1", A, true, -1, 0, 1, 2, 3, 4, 5),
            elevator("2", B, true, 0, 2, 3));

    private static CoreConnection connection(int from, int to) {
        return new CoreConnection(null, A, B, from, to, null, null);
    }

    @Test
    void validLayoutPasses() {
        assertThatNoException().isThrownBy(() -> ElevatorLayoutValidator.validate(
                ELEVATORS, List.of(connection(0, 0), connection(2, 3))));
    }

    @Test
    void floorOutsideTheBuildingIsInvalid() {
        assertThatThrownBy(() -> ElevatorLayoutValidator.validate(List.of(elevator("9", A, true, 0, 6)), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ascensor 9 sirve el piso 6, fuera del rango");
        assertThatThrownBy(() -> ElevatorLayoutValidator.validate(List.of(elevator("9", A, false, -2)), List.of()))
                .hasMessageContaining("ascensor 9 sirve el piso -2");
    }

    @Test
    void connectionOnAFloorNotServedByBothCoresIsInvalid() {
        // B no para en el 1.
        assertThatThrownBy(() -> ElevatorLayoutValidator.validate(ELEVATORS, List.of(connection(0, 3))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("la conexión A–B cubre el piso 1, que ningún ascensor de B sirve");
        // B no llega al 4.
        assertThatThrownBy(() -> ElevatorLayoutValidator.validate(ELEVATORS, List.of(connection(3, 4))))
                .hasMessageContaining("piso 4");
    }

    @Test
    void inactiveElevatorsStillCountSoABreakdownDoesNotPreventStartup() {
        // Todos los ascensores de B fuera de servicio: la conexión sigue siendo válida.
        List<Elevator> allOfBInactive = List.of(
                elevator("1", A, true, 0, 1),
                elevator("2", B, false, 0),
                elevator("3", B, false, 1));
        assertThatNoException().isThrownBy(
                () -> ElevatorLayoutValidator.validate(allOfBInactive, List.of(connection(0, 1))));
    }
}
