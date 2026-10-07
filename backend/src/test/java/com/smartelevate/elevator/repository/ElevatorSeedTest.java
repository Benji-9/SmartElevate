package com.smartelevate.elevator.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartelevate.elevator.model.Elevator;
import com.smartelevate.elevator.model.ElevatorUsage;
import com.smartelevate.elevator.service.ElevatorLayoutValidator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/** El seed de V2__elevators.sql, aplicado por Flyway sobre H2, coincide con asignacion.md y pasa la validación. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ElevatorLayoutValidator.class)
class ElevatorSeedTest {

    @Autowired
    private ElevatorRepository elevators;

    @Autowired
    private CoreConnectionRepository connections;

    private Elevator elevator(String code) {
        return elevators.findById(code).orElseThrow();
    }

    @Test
    void loadsEveryElevatorActive() {
        // IND2 2 + IND1 2 + L3 6 + L2 4 + L1 4.
        assertThat(elevators.findAll()).hasSize(18).allMatch(Elevator::isActive);
        assertThat(connections.findAll()).hasSize(5);
    }

    @Test
    void ind2DoesNotStopAtFloorOne() {
        for (String code : List.of("33", "34")) {
            assertThat(elevator(code).getCore().getCode()).isEqualTo("IND2");
            assertThat(elevator(code).getFloors()).hasSize(15).contains(-4, 0, 2, 11).doesNotContain(1);
        }
    }

    @Test
    void l2HighBatterySkipsMinusOneAndOne() {
        for (String code : List.of("35", "36")) {
            assertThat(elevator(code).getFloors()).contains(-2, 0, 2, 10).doesNotContain(-1, 1);
        }
    }

    @Test
    void ind1ElevatorsAreDedicated() {
        assertThat(elevator("05").getUsage()).isEqualTo(ElevatorUsage.TEACHERS);
        assertThat(elevator("06").getUsage()).isEqualTo(ElevatorUsage.REDUCED_MOBILITY);
        assertThat(elevators.findAll())
                .filteredOn(e -> !List.of("05", "06").contains(e.getCode()))
                .allMatch(e -> e.getUsage() == ElevatorUsage.COMMON);
    }

    @Test
    void floorElevenIsOnlyServedByInd2() {
        assertThat(elevators.findAll())
                .filteredOn(e -> e.getFloors().contains(11))
                .extracting(Elevator::getCode)
                .containsExactlyInAnyOrder("33", "34");
    }
}
