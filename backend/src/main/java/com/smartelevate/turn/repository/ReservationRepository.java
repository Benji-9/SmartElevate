package com.smartelevate.turn.repository;

import com.smartelevate.turn.model.Reservation;
import com.smartelevate.turn.model.ReservationStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * Criterio único (#231): una reserva con check-in {@code SIMULATED} (simulador de admin) queda fuera del conteo
     * de faltas y de los KPIs. Lo usan {@link #faultTimes} y {@link #findRealNoShowsBetween}; alias {@code r}.
     */
    String NOT_SIMULATED = "not exists (select c.id from CheckIn c where c.reservationId = r.id "
            + "and c.source = 'SIMULATED')";

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

    /** Reserva con su salida, ascensor, núcleo y edificio: lo que lleva {@code ReservationResponse}. */
    String WITH_DETAILS = "select r from Reservation r join fetch r.departure d join fetch d.elevator e "
            + "join fetch e.core c join fetch c.building where r.userId = :userId ";
    String NEWEST_FIRST = " order by d.departureAt desc, r.id desc";

    /** Activas del usuario con salida desde {@code from}, la más próxima primero. */
    @Query(WITH_DETAILS + "and r.status = 'ACTIVE' and d.departureAt >= :from order by d.departureAt, r.id")
    List<Reservation> findActiveFrom(@Param("userId") UUID userId, @Param("from") Instant from);

    /** Primera página del historial (keyset sobre salida e id, de la más reciente a la más vieja). */
    @Query(WITH_DETAILS + NEWEST_FIRST)
    List<Reservation> history(@Param("userId") UUID userId, Pageable page);

    /** Página siguiente del historial: las estrictamente anteriores a (at, id) en el mismo orden. */
    @Query(WITH_DETAILS + "and (d.departureAt < :at or (d.departureAt = :at and r.id < :id))" + NEWEST_FIRST)
    List<Reservation> historyBefore(@Param("userId") UUID userId, @Param("at") Instant at, @Param("id") long id,
                                    Pageable page);

    /**
     * Tope diario: reservas hechas en [from, to) salvo las del estado excluido. Se excluye CANCELLED: cancelar sin
     * falta devuelve el lugar en el tope; la tardía (CANCELLED_LATE) no (#218).
     */
    long countByUserIdAndStatusNotAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(UUID userId,
            ReservationStatus excluded, Instant from, Instant to);

    /**
     * Job de no-shows (#231): ACTIVE con salida estrictamente antes de {@code cutoff} pasan a NO_SHOW. Condicional
     * sobre ACTIVE: no pisa un USED ni un cancelado, y correrlo de nuevo no cambia nada.
     */
    @Transactional
    @Modifying
    @Query("update Reservation r set r.status = 'NO_SHOW' where r.status = 'ACTIVE' "
            + "and r.departure.id in (select d.id from Departure d where d.departureAt < :cutoff)")
    int markNoShows(@Param("cutoff") Instant cutoff);

    /**
     * Recordatorio (#221): ACTIVE sin recordatorio reclamado con salida en ({@code after}, {@code until}], con lo
     * que necesita el mail cargado.
     */
    @Query("select r from Reservation r join fetch r.departure d join fetch d.elevator e join fetch e.core c "
            + "join fetch c.building where r.status = 'ACTIVE' and r.reminderSentAt is null "
            + "and d.departureAt > :after and d.departureAt <= :until order by d.departureAt, r.id")
    List<Reservation> findDueForReminder(@Param("after") Instant after, @Param("until") Instant until);

    /** Reclama el recordatorio de la reserva: 1 si lo reclamó ahora, 0 si ya estaba reclamado o dejó de estar ACTIVE. */
    @Transactional
    @Modifying
    @Query("update Reservation r set r.reminderSentAt = :now where r.id = :id and r.reminderSentAt is null "
            + "and r.status = 'ACTIVE'")
    int claimReminder(@Param("id") long id, @Param("now") Instant now);

    /**
     * Horas de salida de las faltas del usuario con salida desde {@code from} inclusive, la más vieja primero.
     * Sin las reservas con check-in simulado ({@link #NOT_SIMULATED}).
     */
    @Query("select d.departureAt from Reservation r join r.departure d where r.userId = :userId "
            + "and r.status in :faults and d.departureAt >= :from and " + NOT_SIMULATED + " order by d.departureAt")
    List<Instant> faultTimes(@Param("userId") UUID userId, @Param("faults") Collection<ReservationStatus> faults,
                             @Param("from") Instant from);

    /** Para {@code KpiEvents}: NO_SHOW con salida en [from, to), sin las que tienen un check-in simulado. */
    @Query("select r from Reservation r join fetch r.departure d where r.status = 'NO_SHOW' "
            + "and d.departureAt >= :from and d.departureAt < :to and " + NOT_SIMULATED)
    List<Reservation> findRealNoShowsBetween(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Para {@code KpiEvents}: reservas no canceladas con salida en [from, to), con su ascensor, núcleo y edificio;
     * sin las que tienen un check-in simulado.
     */
    @Query("select r from Reservation r join fetch r.departure d join fetch d.elevator e join fetch e.core c "
            + "join fetch c.building where r.status in ('ACTIVE', 'USED', 'NO_SHOW') "
            + "and d.departureAt >= :from and d.departureAt < :to and " + NOT_SIMULATED)
    List<Reservation> findRealNotCancelledBetween(@Param("from") Instant from, @Param("to") Instant to);
}
