package com.smartelevate.turn.repository;

import com.smartelevate.turn.model.CheckIn;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    boolean existsByReservationId(long reservationId);

    /** Para {@code KpiEvents}: check-ins de kioscos reales escaneados en [from, to). */
    @Query("select c from CheckIn c where c.source = 'REAL' and c.checkedInAt >= :from and c.checkedInAt < :to "
            + "order by c.checkedInAt")
    List<CheckIn> findRealBetween(@Param("from") Instant from, @Param("to") Instant to);
}
