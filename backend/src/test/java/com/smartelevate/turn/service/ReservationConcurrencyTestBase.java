package com.smartelevate.turn.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartelevate.elevator.model.Elevator;
import com.smartelevate.elevator.repository.ElevatorRepository;
import com.smartelevate.turn.dto.ReserveRequest;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.repository.DepartureRepository;
import com.smartelevate.turn.repository.ReservationRepository;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Cupo atómico con hilos reales y commits reales (sin {@code @Transactional} en el test): el lock pesimista y el
 * constraint único se prueban de verdad. Corre contra H2 (siempre) y contra Postgres (donde hay Docker, y en CI).
 */
abstract class ReservationConcurrencyTestBase {

    // Martes 2026-10-13 12:55 en Buenos Aires.
    static final Instant NOW = Instant.parse("2026-10-13T15:55:00Z");
    private static final int CAPACITY = 10;
    private static final int REQUESTS = 25;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock concurrencyTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private ReservationService service;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private DepartureRepository departures;
    @Autowired
    private ElevatorRepository elevators;
    @Autowired
    private UserRepository users;

    @AfterEach
    void clean() {
        reservations.deleteAll();
        departures.deleteAll();
        users.deleteAll();
    }

    @Test
    void manyUsersOnTheSameDepartureExactlyTenGetASeatAndNoSeatIsRepeated() throws Exception {
        Departure departure = departure(10);
        List<UUID> people = newUsers(REQUESTS);

        List<Object> results = runAtOnce(people.stream()
                .<Callable<Object>>map(id -> () -> attempt(id, departure.getId())).toList());

        assertThat(results.stream().filter(r -> r instanceof Integer)).hasSize(CAPACITY);
        assertThat(results.stream().filter(r -> r instanceof TurnFullException)).hasSize(REQUESTS - CAPACITY);
        List<Integer> seats = reservations.activeSeats(departure.getId());
        assertThat(seats).hasSize(CAPACITY).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, CAPACITY).boxed().toList());
    }

    @Test
    void oneUserFiringSeveralRequestsAtOnceGetsOnlyOneActiveReservation() throws Exception {
        List<Departure> targets = IntStream.range(0, 6).mapToObj(i -> departure(10 + 2 * i)).toList();
        UUID id = newUsers(1).getFirst();

        List<Object> results = runAtOnce(targets.stream()
                .<Callable<Object>>map(d -> () -> attempt(id, d.getId())).toList());

        assertThat(results.stream().filter(r -> r instanceof Integer)).hasSize(1);
        assertThat(results.stream().filter(r -> r instanceof ReservationRejectedException)).hasSize(5)
                .allSatisfy(r -> assertThat(((ReservationRejectedException) r).getReason())
                        .isEqualTo(ReservationRejectedException.Reason.ACTIVE_LIMIT_REACHED));
        assertThat(reservations.count()).isEqualTo(1);
    }

    private Object attempt(UUID userId, long departureId) {
        try {
            return service.reserve(userId, new ReserveRequest(departureId, 0, 8)).seatNumber();
        } catch (TurnFullException | ReservationRejectedException e) {
            return e;
        }
    }

    private Departure departure(int minutesFromNow) {
        Elevator e33 = elevators.findById("33").orElseThrow();
        return departures.save(new Departure(e33, NOW.plus(Duration.ofMinutes(minutesFromNow)), CAPACITY));
    }

    private List<UUID> newUsers(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> users.save(new User("Persona " + i, "p" + i + "@uade.edu.ar", "100" + i, "x",
                        DeclaredUserType.STUDENT, NOW)).getId())
                .toList();
    }

    /** Larga todas las tareas a la vez (compuerta compartida) y devuelve los resultados. */
    private static List<Object> runAtOnce(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
