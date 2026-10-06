package com.smartelevate.elevator.repository;

import com.smartelevate.elevator.model.Elevator;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ElevatorRepository extends JpaRepository<Elevator, String> {
}
