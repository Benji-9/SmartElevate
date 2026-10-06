package com.smartelevate.elevator.repository;

import com.smartelevate.elevator.model.Building;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingRepository extends JpaRepository<Building, String> {
}
