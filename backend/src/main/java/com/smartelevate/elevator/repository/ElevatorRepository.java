package com.smartelevate.elevator.repository;

import com.smartelevate.elevator.model.Elevator;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ElevatorRepository extends JpaRepository<Elevator, String> {

    /** SELECT ... FOR UPDATE: serializa los cambios de servicio de un mismo ascensor (#134). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Elevator e where e.code = :code")
    Optional<Elevator> findByCodeForUpdate(@Param("code") String code);
}
