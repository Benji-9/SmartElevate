package com.smartelevate.turn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartelevate.elevator.model.Elevator;
import com.smartelevate.elevator.repository.ElevatorRepository;
import com.smartelevate.priority.model.PriorityCategory;
import com.smartelevate.priority.model.PriorityStatus;
import com.smartelevate.priority.model.UserPriority;
import com.smartelevate.priority.repository.UserPriorityRepository;
import com.smartelevate.turn.dto.ReservationResponse;
import com.smartelevate.turn.dto.ReserveRequest;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.ReservationStatus;
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
    @Autowired
    private UserPriorityRepository priorities;

    @AfterEach
    void clean() {
        reservations.deleteAll();
        departures.deleteAll();
        priorities.deleteAll();
        users.deleteAll();
    }

    @Test
    void priorityAndCommonUsersCompetingNeverExceedCapacityNorTakeTheReservedSeats() throws Exception {
        Departure departure = departure(10);
        List<UUID> common = newUsers(20);
        List<UUID> priority = newUsers(20, 5).stream()
                .peek(id -> approve(id, PriorityCategory.TEACHER, null)).toList();
        List<Callable<Object>> tasks = new ArrayList<>();
        common.forEach(id -> tasks.add(() -> attempt(id, departure.getId())));
        priority.forEach(id -> tasks.add(() -> attempt(id, departure.getId())));
        java.util.Collections.shuffle(tasks);

        List<Object> results = runAtOnce(tasks);

        assertThat(results.stream().filter(r -> r instanceof Integer)).hasSize(CAPACITY);
        List<Integer> seats = reservations.activeSeats(departure.getId());
        assertThat(seats).hasSize(CAPACITY).doesNotHaveDuplicates()
                .allSatisfy(seat -> assertThat(seat).isBetween(1, CAPACITY));
        // Los comunes nunca ocupan los 2 reservados (como mucho 8): siempre entran al menos 2 prioritarios.
        assertThat(reservations.countActivePriority(departure.getId())).isBetween(2L, 5L);
    }

    @Test
    void anExpiredPriorityIsTreatedAsACommonUser() {
        Departure dedicated = departures.save(new Departure(elevators.findById("06").orElseThrow(),
                NOW.plus(Duration.ofMinutes(10)), CAPACITY));
        UUID expired = newUsers(1).getFirst();
        approve(expired, PriorityCategory.REDUCED_MOBILITY, NOW.minusSeconds(1));
        UUID current = newUsers(1, 1).getFirst();
        approve(current, PriorityCategory.REDUCED_MOBILITY, NOW.plusSeconds(60));

        assertThat(attempt(expired, dedicated.getId())).isInstanceOfSatisfying(ReservationRejectedException.class,
                e -> assertThat(e.getReason()).isEqualTo(ReservationRejectedException.Reason.ELEVATOR_NOT_BOOKABLE));
        assertThat(attempt(current, dedicated.getId())).isEqualTo(1);
    }

    @Test
    void aCancelledPrioritySeatGoesBackToPriorityUsersUntilTheReleaseAndToAnyoneAfter() {
        // A 10 min los 2 lugares siguen reservados; a 3 min ya se liberaron.
        for (int minutes : new int[] {10, 3}) {
            Departure departure = departure(minutes);
            int offset = minutes * 100; // usuarios distintos en cada salida (1 reserva activa por usuario)
            List<UUID> common = newUsers(offset, 9);
            List<UUID> priority = newUsers(offset + 9, 3);
            priority.forEach(id -> approve(id, PriorityCategory.TEACHER, null));
            common.subList(0, 8).forEach(id -> service.reserve(id, new ReserveRequest(departure.getId(), 0, 8)));
            ReservationResponse first = service.reserve(priority.get(0), new ReserveRequest(departure.getId(), 0, 8));
            service.reserve(priority.get(1), new ReserveRequest(departure.getId(), 0, 8));

            service.cancel(priority.get(0), first.id());

            assertThat(reservations.countActivePriority(departure.getId())).isEqualTo(1);
            if (minutes == 10) {
                assertThat(attempt(common.getLast(), departure.getId())).isInstanceOf(TurnFullException.class);
                assertThat(attempt(priority.get(2), departure.getId())).isEqualTo(first.seatNumber());
            } else {
                assertThat(attempt(common.getLast(), departure.getId())).isEqualTo(first.seatNumber());
            }
            assertThat(reservations.activeSeats(departure.getId())).hasSize(CAPACITY).doesNotHaveDuplicates();
        }
    }

    private void approve(UUID userId, PriorityCategory category, Instant expiresAt) {
        UserPriority p = new UserPriority(userId, category);
        p.decide(PriorityStatus.APPROVED, expiresAt, NOW.minus(Duration.ofDays(1)), userId);
        priorities.save(p);
    }

    @Test
    void manyUsersOnTheSameDepartureExactlyTenGetASeatAndNoSeatIsRepeated() throws Exception {
        Departure departure = departure(3); // a 3 min ya se liberaron los lugares de prioritarios
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

    @Test
    void cancellingFreesTheSeatForSomeoneElseRightAwayAndStrangersCannotTouchIt() {
        Departure departure = departure(3); // lugares de prioritarios ya liberados: entran 10 comunes
        List<UUID> people = newUsers(CAPACITY + 1);
        List<ReservationResponse> booked = people.subList(0, CAPACITY).stream()
                .map(id -> service.reserve(id, new ReserveRequest(departure.getId(), 0, 8))).toList();
        UUID late = people.getLast();
        assertThat(attempt(late, departure.getId())).isInstanceOf(TurnFullException.class);

        ReservationResponse third = booked.get(2);
        UUID owner = people.get(2);
        assertThat(service.history(late, null).items()).isEmpty();
        assertThatThrownBy(() -> service.cancel(late, third.id()))
                .isInstanceOfSatisfying(ReservationRejectedException.class, e -> assertThat(e.getReason())
                        .isEqualTo(ReservationRejectedException.Reason.RESERVATION_NOT_FOUND));

        service.cancel(owner, third.id());
        service.cancel(owner, third.id());

        // A 3 min de la salida ya pasó el plazo sin falta (10 min, V18): es tardía, pero libera el lugar igual.
        assertThat(service.history(owner, null).items()).singleElement().satisfies(r -> {
            assertThat(r.status()).isEqualTo(ReservationStatus.CANCELLED_LATE);
            assertThat(r.seatNumber()).isNull();
        });
        assertThat(attempt(late, departure.getId())).isEqualTo(third.seatNumber());
    }

    @Test
    void aCancelRacingWithManyReservesLetsExactlyOneIn() throws Exception {
        Departure departure = departure(3); // lugares de prioritarios ya liberados: entran 10 comunes
        List<UUID> people = newUsers(CAPACITY + 6);
        List<ReservationResponse> booked = people.subList(0, CAPACITY).stream()
                .map(id -> service.reserve(id, new ReserveRequest(departure.getId(), 0, 8))).toList();
        List<Callable<Object>> tasks = new ArrayList<>(people.subList(CAPACITY, people.size()).stream()
                .<Callable<Object>>map(id -> () -> attempt(id, departure.getId())).toList());
        tasks.add(() -> {
            service.cancel(people.getFirst(), booked.getFirst().id());
            return "cancelled";
        });

        List<Object> results = runAtOnce(tasks);

        // Según quién gane el lock, entra uno solo (si el cancel llega antes) o ninguno.
        long winners = results.stream().filter(r -> r instanceof Integer).count();
        assertThat(winners).isLessThanOrEqualTo(1);
        assertThat(reservations.activeSeats(departure.getId())).doesNotHaveDuplicates()
                .hasSize(CAPACITY - 1 + (int) winners);
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
        return newUsers(0, count);
    }

    private List<UUID> newUsers(int from, int count) {
        return IntStream.range(from, from + count)
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
