package com.smartelevate.turn.repository;

import com.smartelevate.elevator.model.Elevator;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.DepartureStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartureRepository extends JpaRepository<Departure, Long> {

    List<Departure> findByElevatorInAndStatusAndDepartureAtBetweenOrderByDepartureAtAscElevatorCodeAsc(
            Collection<Elevator> elevators, DepartureStatus status, Instant from, Instant to);
}
