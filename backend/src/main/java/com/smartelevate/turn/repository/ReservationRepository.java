package com.smartelevate.turn.repository;

import com.smartelevate.turn.model.Reservation;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("select r.seatNumber from Reservation r where r.departure.id = :departureId and r.status = 'ACTIVE'")
    List<Integer> activeSeats(@Param("departureId") long departureId);

    @Query("select count(r) from Reservation r where r.departure.id = :departureId and r.status = 'ACTIVE' "
            + "and r.priorityUser = true")
    long countActivePriority(@Param("departureId") long departureId);

    /**
     * Filas [id de salida, reservas activas, de ellas de prioritarios] de las salidas dadas (las que no tienen
     * reservas no aparecen).
     */
    @Query("select r.departure.id, count(r), sum(case when r.priorityUser = true then 1 else 0 end) "
            + "from Reservation r where r.departure.id in :ids and r.status = 'ACTIVE' group by r.departure.id")
    List<Object[]> countActiveByDeparture(@Param("ids") Collection<Long> ids);

    /** Reservas activas cuya salida todavía no pasó. */
    @Query("select count(r) from Reservation r where r.userId = :userId and r.status = 'ACTIVE' "
            + "and r.departure.departureAt > :now")
    long countOngoing(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Reservas del usuario con salida desde {@code from}, de cualquier estado, la salida más tardía primero. */
    @Query("select r from Reservation r join fetch r.departure d join fetch d.elevator "
            + "where r.userId = :userId and d.departureAt >= :from order by d.departureAt desc, r.id desc")
    List<Reservation> findMine(@Param("userId") UUID userId, @Param("from") Instant from);

    /** Reservas hechas en [from, to), de cualquier estado: cancelar no devuelve el cupo diario. */
    long countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(UUID userId, Instant from, Instant to);
}
