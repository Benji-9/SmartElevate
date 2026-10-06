package com.smartelevate.elevator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartelevate.elevator.model.CoreConnection;
import com.smartelevate.elevator.repository.CoreConnectionRepository;
import com.smartelevate.elevator.service.ConnectionGraphService.AlternativeCore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/** BFS sobre las conexiones del seed de V2 (Flyway sobre H2). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ConnectionGraphService.class)
class ConnectionGraphSeedTest {

    @Autowired
    private ConnectionGraphService graph;

    @Autowired
    private CoreConnectionRepository connections;

    @Test
    void seedChainFromL1() {
        assertThat(graph.alternatives("L1", 5, false)).containsExactly(
                new AlternativeCore("L2", 1, 0, false),
                new AlternativeCore("L3", 2, 0, false),
                new AlternativeCore("IND1", 3, 0, false),
                new AlternativeCore("IND2", 4, 0, false));
    }

    @Test
    void seedIsolatedFloorsAndUnconfirmedAccessibility() {
        assertThat(graph.alternatives("IND2", 11, false)).isEmpty();
        assertThat(graph.alternatives("IND2", 1, false)).isEmpty();
        assertThat(graph.alternatives("L1", 5, true)).isEmpty();
    }

    @Test
    void editingAConnectionChangesTheResult() {
        CoreConnection l2l1 = connections.findAll().stream()
                .filter(c -> c.getCoreA().getCode().equals("L2") && c.getCoreB().getCode().equals("L1"))
                .findFirst().orElseThrow();
        connections.delete(l2l1);
        connections.save(new CoreConnection(null, l2l1.getCoreA(), l2l1.getCoreB(), -3, 7, 45, true));

        assertThat(graph.alternatives("L1", 5, true)).containsExactly(new AlternativeCore("L2", 1, 45, true));
    }
}
