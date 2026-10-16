package com.smartelevate.turn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.smartelevate.elevator.model.Kiosk;
import com.smartelevate.elevator.repository.ElevatorRepository;
import com.smartelevate.elevator.repository.KioskRepository;
import com.smartelevate.elevator.service.QrTokenService;
import com.smartelevate.priority.repository.PriorityAuditRepository;
import com.smartelevate.turn.dto.ElevatorServiceResponse;
import com.smartelevate.turn.dto.ReserveRequest;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.turn.model.ReservationStatus;
import com.smartelevate.turn.repository.CheckInRepository;
import com.smartelevate.turn.repository.DepartureRepository;
import com.smartelevate.turn.repository.ReservationRepository;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Carreras entre "ascensor fuera de servicio" y reservar, cancelar y hacer check-in (#134), deterministas: un hilo
 * abre una transacción, hace su operación (los locks quedan tomados) y espera un latch antes de commitear; el otro
 * pedido arranca, se bloquea en el lock de la salida y recién se libera cuando el primero commitea. Así se prueba
 * cada orden de llegada sin depender de los tiempos. Corre contra H2 y contra Postgres.
 */
abstract class ElevatorOutageRaceTestBase {

    private static final Instant NOW = ReservationConcurrencyTestBase.NOW;
    private static final long EXTRA_MARGIN_MILLIS = 300; // menos que el lock timeout de H2 (2 s)

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private CheckInService checkInService;
    @Autowired
    private ElevatorOutageService outage;
    @Autowired
    private QrTokenService qrTokens;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private DepartureRepository departures;
    @Autowired
    private ElevatorRepository elevators;
    @Autowired
    private UserRepository users;
    @Autowired
    private KioskRepository kiosks;
    @Autowired
    private CheckInRepository checkIns;
    @Autowired
    private PriorityAuditRepository audit;
    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private DataSource dataSource;

    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final List<Future<?>> inFlight = new CopyOnWriteArrayList<>();
    private final List<CountDownLatch> releases = new CopyOnWriteArrayList<>();
    private int counter;

    @AfterEach
    void clean() {
        // Primero se suelta y espera a los hilos (aunque el test haya fallado), recién después se borra.
        releases.forEach(CountDownLatch::countDown);
        for (Future<?> f : inFlight) {
            try {
                f.get(30, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // el test ya reportó su propio fallo
            }
        }
        pool.shutdownNow();
        checkIns.deleteAll();
        kiosks.deleteAll();
        audit.deleteAll();
        elevators.findAll().forEach(e -> {
            e.setActive(true);
            elevators.save(e);
        });
        reservations.deleteAll();
        departures.deleteAll();
        users.deleteAll();
    }

    @Test
    void aReserveWaitingForTheOutageIsRejectedOnceItCommits() throws Exception {
        UUID user = newUser();
        Departure departure = departure(Duration.ofMinutes(10));

        Object result = raceHeldBy(() -> outage.markOutOfService(user, "33", "carrera"),
                () -> reservationService.reserve(user, new ReserveRequest(departure.getId(), 0, 8)));

        assertThat(result).isInstanceOfSatisfying(ReservationRejectedException.class, e -> assertThat(e.getReason())
                .isEqualTo(ReservationRejectedException.Reason.ELEVATOR_NOT_BOOKABLE));
        assertThat(reservations.count()).isZero();
        assertThat(elevators.findById("33").orElseThrow().isActive()).isFalse();
    }

    @Test
    void anOutageWaitingForAReserveCancelsItWithoutAFault() throws Exception {
        UUID user = newUser();
        Departure departure = departure(Duration.ofMinutes(10));

        Object result = raceHeldBy(() -> reservationService.reserve(user, new ReserveRequest(departure.getId(), 0, 8)),
                () -> outage.markOutOfService(user, "33", "carrera"));

        assertThat(((ElevatorServiceResponse) result).cancelledReservations()).isEqualTo(1);
        assertThat(onlyReservation().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void aCancelWaitingForTheOutageSeesItsCancellationAndIsNotALateFault() throws Exception {
        UUID user = newUser();
        // A 3 min de la salida una cancelación del usuario sería tardía (falta); la del sistema nunca lo es.
        Reservation reservation = reservations.save(
                new Reservation(user, departure(Duration.ofMinutes(3)), 1, 0, 8, NOW));

        raceHeldBy(() -> outage.markOutOfService(user, "33", "carrera"),
                () -> {
                    reservationService.cancel(user, reservation.getId());
                    return "cancelled";
                });

        assertThat(onlyReservation().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void anOutageWaitingForACancelFindsNothingLeftToCancel() throws Exception {
        UUID user = newUser();
        Reservation reservation = reservations.save(
                new Reservation(user, departure(Duration.ofMinutes(20)), 1, 0, 8, NOW));

        Object result = raceHeldBy(() -> {
                    reservationService.cancel(user, reservation.getId());
                    return "cancelled";
                },
                () -> outage.markOutOfService(user, "33", "carrera"));

        assertThat(((ElevatorServiceResponse) result).cancelledReservations()).isZero();
        assertThat(onlyReservation().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void aCheckInWaitingForTheOutageIsRejectedAndDoesNotOverwriteTheCancellation() throws Exception {
        UUID user = newUser();
        Reservation reservation = reservations.save(
                new Reservation(user, departure(Duration.ofSeconds(10)), 1, 0, 8, NOW));
        String token = token();

        Object result = raceHeldBy(() -> outage.markOutOfService(user, "33", "carrera"),
                () -> checkInService.checkIn(user, token));

        assertThat(result).isInstanceOfSatisfying(ReservationRejectedException.class, e -> assertThat(e.getReason())
                .isEqualTo(ReservationRejectedException.Reason.NO_ACTIVE_RESERVATION));
        Reservation after = reservations.findById(reservation.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(after.getSeatNumber()).isNull();
        assertThat(checkIns.count()).isZero();
    }

    @Test
    void anOutageWaitingForACheckInLeavesTheUsedReservationAlone() throws Exception {
        UUID user = newUser();
        reservations.save(new Reservation(user, departure(Duration.ofSeconds(10)), 1, 0, 8, NOW));
        String token = token();

        Object result = raceHeldBy(() -> checkInService.checkIn(user, token),
                () -> outage.markOutOfService(user, "33", "carrera"));

        assertThat(((ElevatorServiceResponse) result).cancelledReservations()).isZero();
        assertThat(onlyReservation().getStatus()).isEqualTo(ReservationStatus.USED);
        assertThat(checkIns.count()).isEqualTo(1);
    }

    /**
     * {@code holder} corre en una transacción que se mantiene abierta (con sus locks) hasta que el {@code waiter},
     * en otro hilo, está bloqueado; recién entonces commitea. Devuelve el resultado del waiter, o la excepción de
     * dominio si la lanzó.
     */
    private Object raceHeldBy(Callable<?> holder, Callable<?> waiter) throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        releases.add(release);
        Future<Object> holding = pool.submit(() -> new TransactionTemplate(txManager).execute(status -> {
            try {
                Object result = holder.call();
                held.countDown();
                release.await();
                return result;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                held.countDown();
            }
        }));
        inFlight.add(holding);
        assertThat(held.await(30, TimeUnit.SECONDS)).isTrue();
        CountDownLatch waiterStarted = new CountDownLatch(1);
        Future<Object> waiting = pool.submit(() -> {
            waiterStarted.countDown(); // justo antes de llamar al servicio
            try {
                return waiter.call();
            } catch (ReservationRejectedException | TurnFullException e) {
                return e;
            }
        });
        inFlight.add(waiting);
        assertThat(waiterStarted.await(30, TimeUnit.SECONDS)).isTrue();
        awaitBlocked(waiting);
        assertThat(waiting.isDone()).as("el segundo pedido espera el lock de la salida").isFalse();
        release.countDown();
        holding.get(30, TimeUnit.SECONDS);
        try {
            return waiting.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    /**
     * Espera (acotado) a que el waiter esté bloqueado en el lock. En Postgres se mira {@code pg_stat_activity} y, si en
     * 10 s no aparece una sesión esperando un lock, el test falla. H2 no expone el bloqueo: no hay forma de
     * observarlo, así que el margen fijo es el límite (y tiene que ser menor que el lock timeout de 2 s de H2).
     */
    private void awaitBlocked(Future<?> waiting) throws Exception {
        if (!isPostgres()) {
            Thread.sleep(EXTRA_MARGIN_MILLIS);
            return;
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline && !waiting.isDone()) {
            Integer locked = jdbc.queryForObject("select count(*) from pg_stat_activity where datname = "
                    + "current_database() and pid <> pg_backend_pid() and wait_event_type = 'Lock'", Integer.class);
            if (locked != null && locked > 0) {
                return;
            }
            Thread.sleep(20);
        }
        fail("el waiter no llegó a bloquearse en el lock");
    }

    private boolean isPostgres() throws Exception {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
        }
    }

    private Reservation onlyReservation() {
        return reservations.findAll().getFirst();
    }

    private Departure departure(Duration fromNow) {
        return departures.save(new Departure(elevators.findById("33").orElseThrow(), NOW.plus(fromNow), 10));
    }

    private UUID newUser() {
        int n = ++counter;
        return users.save(new User("Persona " + n, "race" + n + "@uade.edu.ar", "300" + n, "x",
                DeclaredUserType.STUDENT, NOW)).getId();
    }

    private String token() {
        Kiosk kiosk = kiosks.save(new Kiosk("33", UUID.randomUUID().toString().replace("-", ""), false, NOW));
        return qrTokens.issue(kiosk).token();
    }
}
