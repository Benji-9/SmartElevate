package com.smartelevate.turn.service;

import static com.smartelevate.priority.model.PriorityCategory.NONE;
import static com.smartelevate.priority.model.PriorityCategory.REDUCED_MOBILITY;
import static com.smartelevate.priority.model.PriorityCategory.TEACHER;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AssignmentRequestTest {

    private static final Instant T0 = Instant.parse("2026-10-13T15:55:00Z");

    @Test
    void ordersByReducedMobilityThenTeachersThenHighestFloorThenArrival() {
        List<AssignmentRequest> expected = List.of(
                new AssignmentRequest(REDUCED_MOBILITY, 2, T0.plusSeconds(50)), // piso bajo, pero movilidad reducida
                new AssignmentRequest(REDUCED_MOBILITY, 2, T0.plusSeconds(60)),
                new AssignmentRequest(TEACHER, 5, T0.plusSeconds(40)),
                new AssignmentRequest(NONE, 10, T0.plusSeconds(30)),
                new AssignmentRequest(NONE, 8, T0),
                new AssignmentRequest(NONE, 8, T0.plusSeconds(1)),
                new AssignmentRequest(NONE, -2, T0));
        List<AssignmentRequest> shuffled = new ArrayList<>(expected);
        Collections.shuffle(shuffled, new Random(117));

        shuffled.sort(AssignmentRequest.ORDER);

        assertThat(shuffled).containsExactlyElementsOf(expected);
    }
}
