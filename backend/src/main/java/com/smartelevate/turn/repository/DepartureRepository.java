package com.smartelevate.turn.repository;

import com.smartelevate.elevator.model.Elevator;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.DepartureStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartureRepository extends JpaRepository<Departure, Long> {

    /** SELECT ... FOR UPDATE: serializa las reservas de una misma salida (cupo atómico, ADR 0010). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Departure d where d.id = :id")
    Optional<Departure> findByIdForUpdate(@Param("id") long id);

    List<Departure> findByElevatorInAndStatusAndDepartureAtBetweenOrderByDepartureAtAscElevatorCodeAsc(
            Collection<Elevator> elevators, DepartureStatus status, Instant from, Instant to);

    /** Salidas programadas de todos los ascensores en [from, to], con su ascensor y núcleo (congestión, #220). */
    @Query("select d from Departure d join fetch d.elevator e join fetch e.core where d.status = 'SCHEDULED' "
            + "and d.departureAt >= :from and d.departureAt <= :to")
    List<Departure> findScheduledBetween(@Param("from") Instant from, @Param("to") Instant to);
}
