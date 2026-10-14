package com.smartelevate.turn.repository;

import com.smartelevate.turn.model.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    boolean existsByReservationId(long reservationId);
}
