package com.smartelevate.turn.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartelevate.priority.model.PriorityCategory;
import com.smartelevate.priority.model.PriorityStatus;
import com.smartelevate.priority.model.UserPriority;
import com.smartelevate.turn.dto.DepartureResponse;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.User;
import java.util.UUID;
import com.smartelevate.turn.repository.DepartureRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/** Generación de salidas contra la base real (H2 con las migraciones) y un reloj fijo. */
@SpringBootTest
@Transactional
class DepartureServiceTest {

    // 2026-10-13 es martes. Buenos Aires es UTC-3 todo el año.
    private static final AtomicReference<Instant> NOW = new AtomicReference<>(Instant.parse("2026-10-13T15:55:00Z")); // el arranque ya lee la hora (ConfigService)
    /** Sin prioridad en la base: usuario común. */
    private static final UUID COMMON = UUID.randomUUID();

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return new Clock() {
                @Override
                public java.time.ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(java.time.ZoneId zone) {
                    return Clock.fixed(NOW.get(), zone);
                }

                @Override
                public Instant instant() {
                    return NOW.get();
                }
            };
        }
    }

    @Autowired
    private DepartureService service;

    @Autowired
    private DepartureRepository repository;

    @Autowired
    private com.smartelevate.turn.repository.ReservationRepository reservations;

    @Autowired
    private com.smartelevate.user.repository.UserRepository users;

    @Autowired
    private com.smartelevate.priority.repository.UserPriorityRepository priorities;

    private UUID user(String name) {
        return users.save(new User(name, name + "@uade.edu.ar", String.valueOf(Math.abs(name.hashCode())), "x",
                DeclaredUserType.STUDENT, NOW.get())).getId();
    }

    private UUID priorityUser(String name, PriorityCategory category) {
        UUID id = user(name);
        UserPriority p = new UserPriority(id, category);
        p.decide(PriorityStatus.APPROVED, null, NOW.get(), id);
        priorities.save(p);
        return id;
    }

    @Test
    void tuesdayAt1255ReturnsTheExpectedSlotsInUtc() {
        NOW.set(Instant.parse("2026-10-13T15:55:00Z")); // 12:55 BA

        List<DepartureResponse> result = service.findBookable(COMMON, "33", null);

        // Ventana 12:57 a 13:25 BA, salidas cada 2 min desde las 07:00: 12:58 ... 13:24 = 14 salidas.
        assertThat(result).hasSize(14);
        assertThat(result.getFirst().departureAt()).isEqualTo(Instant.parse("2026-10-13T15:58:00Z"));
        assertThat(result.getLast().departureAt()).isEqualTo(Instant.parse("2026-10-13T16:24:00Z"));
        // A un común no se le cuentan los 2 reservados a prioritarios, salvo en la de 12:58 (ya liberados: faltan 3 min).
        assertThat(result.getFirst().remainingSeats()).isEqualTo(10);
        assertThat(result.subList(1, result.size())).allSatisfy(d -> assertThat(d.remainingSeats()).isEqualTo(8));
        // Sin reservas, la barra de ocupación no cuenta los lugares guardados: 0 reservados, y cierra 2 min antes.
        assertThat(result).allSatisfy(d -> {
            assertThat(d.bookedSeats()).isZero();
            assertThat(d.bookingClosesAt()).isEqualTo(d.departureAt().minusSeconds(120));
        });
        assertThat(result).allSatisfy(d -> {
            assertThat(d.capacity()).isEqualTo(10);
            assertThat(d.elevator()).isEqualTo("33");
            assertThat(d.building()).isEqualTo("INDEPENDENCIA");
        });
    }

    @Test
    void remainingSeatsDiscountsActiveReservations() {
        NOW.set(Instant.parse("2026-10-13T15:55:00Z"));
        Departure first = repositoryDeparture(service.findBookable(COMMON, "33", null).getFirst().id());
        for (int seat = 1; seat <= 3; seat++) {
            UUID id = users.save(new User("P" + seat, "p" + seat + "@uade.edu.ar", "20" + seat, "x",
                    DeclaredUserType.STUDENT, NOW.get())).getId();
            reservations.save(new Reservation(id, first, seat, 0, 8, NOW.get()));
        }

        List<DepartureResponse> result = service.findBookable(COMMON, "33", null);

        assertThat(result.getFirst().remainingSeats()).isEqualTo(7);
        assertThat(result.get(1).remainingSeats()).isEqualTo(8);
        assertThat(result.getFirst().bookedSeats()).isEqualTo(3);
    }

    @Test
    void bookedSeatsIgnoresCancelledReservations() {
        NOW.set(Instant.parse("2026-10-13T15:55:00Z"));
        Departure first = repositoryDeparture(service.findBookable(COMMON, "33", null).getFirst().id());
        reservations.save(new Reservation(user("active"), first, 1, 0, 8, NOW.get()));
        Reservation cancelled = new Reservation(user("cancelled"), first, 2, 0, 8, NOW.get());
        cancelled.cancelByOutage();
        reservations.save(cancelled);

        DepartureResponse result = service.findBookable(COMMON, "33", null).getFirst();

        assertThat(result.bookedSeats()).isEqualTo(1);
        assertThat(result.remainingSeats()).isEqualTo(9);
    }

    private Departure repositoryDeparture(long id) {
        return repository.findById(id).orElseThrow();
    }

    @Test
    void generationIsIdempotent() {
        NOW.set(Instant.parse("2026-10-13T15:55:00Z"));

        service.findBookable(COMMON, "33", null);
        long first = repository.count();
        List<DepartureResponse> again = service.findBookable(COMMON, "33", null);

        assertThat(again).hasSize(14);
        assertThat(repository.count()).isEqualTo(first).isEqualTo(14);
    }

    @Test
    void commonUsersDoNotSeeTheSeatsHeldForPriorityUsers() {
        NOW.set(Instant.parse("2026-10-13T15:55:00Z"));
        List<DepartureResponse> before = service.findBookable(COMMON, "33", null);
        // La de 13:00 queda a más de 3 min: retiene los 2 lugares de prioritarios.
        Departure second = repositoryDeparture(before.get(1).id());
        for (int seat = 1; seat <= 8; seat++) {
            reservations.save(new Reservation(user("c" + seat), second, seat, 0, 8, NOW.get()));
        }
        UUID teacher = priorityUser("docente", PriorityCategory.TEACHER);

        assertThat(service.findBookable(COMMON, "33", null).get(1).remainingSeats()).isZero();
        assertThat(service.findBookable(teacher, "33", null).get(1).remainingSeats()).isEqualTo(2);
        assertThat(service.findBookable(teacher, "33", null).getLast().remainingSeats()).isEqualTo(10);
    }

    @Test
    void reservedSeatsAreFreeForEveryoneFromThreeMinutesBefore() {
        NOW.set(Instant.parse("2026-10-13T15:55:00Z")); // primera salida 12:58: faltan exactamente 3 min

        assertThat(service.findBookable(COMMON, "33", null).getFirst().remainingSeats()).isEqualTo(10);
    }

    @Test
    void priorityUsersAlsoSeeTheElevatorDedicatedToTheirCategory() {
        NOW.set(Instant.parse("2026-10-13T15:55:00Z"));
        UUID teacher = priorityUser("docente", PriorityCategory.TEACHER);
        UUID reduced = priorityUser("movilidad", PriorityCategory.REDUCED_MOBILITY);

        assertThat(service.findBookable(teacher, null, "independencia")).extracting(DepartureResponse::elevator)
                .containsOnly("33", "34", "05");
        assertThat(service.findBookable(reduced, null, "independencia")).extracting(DepartureResponse::elevator)
                .containsOnly("33", "34", "06");
        // En el dedicado no se retienen lugares: están todos para su categoría.
        assertThat(service.findBookable(teacher, "05", null)).allSatisfy(d ->
                assertThat(d.remainingSeats()).isEqualTo(10));
    }

    @Test
    void excludesDedicatedElevatorsForCommonUsersAndFiltersByBuilding() {
        NOW.set(Instant.parse("2026-10-13T15:55:00Z"));

        assertThat(service.findBookable(COMMON, "05", null)).isEmpty(); // docentes
        assertThat(service.findBookable(COMMON, "06", null)).isEmpty(); // movilidad reducida
        List<DepartureResponse> independencia = service.findBookable(COMMON, null, "independencia");
        assertThat(independencia).extracting(DepartureResponse::elevator).containsOnly("33", "34");
        assertThat(independencia).hasSize(28);
    }

    @Autowired
    private com.smartelevate.elevator.repository.ElevatorRepository elevators;

    /** Salida generada del ascensor a esa hora, con {@code count} reservas activas a {@code destination}. */
    private Departure bookedDeparture(String elevator, String at, int count, int destination) {
        long id = service.findBookable(COMMON, elevator, null).stream()
                .filter(d -> d.departureAt().equals(Instant.parse(at))).findFirst().orElseThrow().id();
        Departure departure = repositoryDeparture(id);
        for (int seat = 1; seat <= count; seat++) {
            reservations.save(new Reservation(user(("u" + elevator + at + seat).toLowerCase()), departure, seat, 0, destination, NOW.get()));
        }
        return departure;
    }

    private static String offeredAt(List<DepartureResponse> offer, String at) {
        return offer.stream().filter(d -> d.departureAt().equals(Instant.parse(at))).findFirst().orElseThrow()
                .elevator();
    }

    @Test
    void offerIsOnePerHour() {
        NOW.set(Instant.parse("2026-10-13T15:55:00Z"));

        List<DepartureResponse> offer = service.findOffer(COMMON, "L1", 0, 7);

        // Las 14 horas de la ventana, una salida por hora, sin reservas: la zona semilla del 7 (03).
        assertThat(offer).hasSize(14);
        assertThat(offer).extracting(DepartureResponse::departureAt).doesNotHaveDuplicates().isSorted();
        // A 5 min o menos (12:58 y 13:00) la zona está abierta: más lugares libres, menor código.
        assertThat(offer.subList(0, 2)).extracting(DepartureResponse::elevator).containsOnly("01");
        assertThat(offer.subList(2, 14)).extracting(DepartureResponse::elevator).containsOnly("03");
    }

    @Test
    void opensAtExactlyFiveMinutesBefore() {
        NOW.set(Instant.parse("2026-10-13T15:55:00Z"));
        // 13:00 (faltan 5 min justos) y 13:02 (faltan 7): el 03 ya lleva a alguien al 7.
        bookedDeparture("03", "2026-10-13T16:00:00Z", 1, 7);
        bookedDeparture("03", "2026-10-13T16:02:00Z", 1, 7);

        List<DepartureResponse> offer = service.findOffer(COMMON, "L1", 0, 7);

        assertThat(offeredAt(offer, "2026-10-13T16:00:00Z")).isEqualTo("01"); // abierta: más lugares libres
        assertThat(offeredAt(offer, "2026-10-13T16:02:00Z")).isEqualTo("03"); // afinidad
    }

    @Test
    void inactiveElevatorIsNotCandidate() {
        NOW.set(Instant.parse("2026-10-13T15:55:00Z"));
        elevators.findById("03").orElseThrow().setActive(false);

        List<DepartureResponse> offer = service.findOffer(COMMON, "L1", 0, 7);

        assertThat(offer).hasSize(14);
        assertThat(offer.subList(2, 14)).extracting(DepartureResponse::elevator).containsOnly("04");
    }

    @Test
    void dedicatedElevatorsAreNotZoned() {
        NOW.set(Instant.parse("2026-10-13T15:55:00Z"));
        UUID teacher = priorityUser("docente", PriorityCategory.TEACHER);

        List<DepartureResponse> offer = service.findOffer(teacher, "IND1", 0, 7);

        assertThat(offer).hasSize(14).extracting(DepartureResponse::elevator).containsOnly("05");
        assertThat(service.findOffer(COMMON, "IND1", 0, 7)).isEmpty();
    }

    @Test
    void commonUserDoesNotGetPriorityHeldSeats() {
        NOW.set(Instant.parse("2026-10-13T15:55:00Z"));
        // 13:10: el 03 tiene 8 reservas al 7; los 2 lugares que quedan son de prioritarios.
        bookedDeparture("03", "2026-10-13T16:10:00Z", 8, 7);
        UUID teacher = priorityUser("docente", PriorityCategory.TEACHER);

        assertThat(offeredAt(service.findOffer(COMMON, "L1", 0, 7), "2026-10-13T16:10:00Z")).isEqualTo("04");
        DepartureResponse forTeacher = service.findOffer(teacher, "L1", 0, 7).stream()
                .filter(d -> d.departureAt().equals(Instant.parse("2026-10-13T16:10:00Z"))).findFirst().orElseThrow();
        assertThat(forTeacher.elevator()).isEqualTo("03");
        assertThat(forTeacher.remainingSeats()).isEqualTo(2);
    }

    @Test
    void offerIsEmptyWhenNoElevatorServesBothFloors() {
        NOW.set(Instant.parse("2026-10-13T15:55:00Z"));

        assertThat(service.findOffer(COMMON, "L1", 0, 9)).isEmpty(); // L1 llega al 7
        assertThat(service.findOffer(COMMON, "XX", 0, 7)).isEmpty();
    }

    @Test
    void doesNotGenerateBeforeOperatingStart() {
        NOW.set(Instant.parse("2026-10-13T09:40:00Z")); // 06:40 BA, ventana 06:42 a 07:10

        List<DepartureResponse> result = service.findBookable(COMMON, "33", null);

        assertThat(result).hasSize(6); // 07:00 a 07:10
        assertThat(result.getFirst().departureAt()).isEqualTo(Instant.parse("2026-10-13T10:00:00Z"));
    }

    @Test
    void doesNotGenerateAtOrAfterOperatingEndEvenPastUtcMidnight() {
        NOW.set(Instant.parse("2026-10-14T01:20:00Z")); // 22:20 BA, ya es mañana en UTC

        List<DepartureResponse> result = service.findBookable(COMMON, "33", null);

        // Ventana 22:22 a 22:50: solo hasta las 22:28 (el horario es [07:00, 22:30)).
        assertThat(result).hasSize(4);
        assertThat(result.getLast().departureAt()).isEqualTo(Instant.parse("2026-10-14T01:28:00Z"));
    }

    @Test
    void returnsNothingWhenTheWindowIsOutsideOperatingHours() {
        NOW.set(Instant.parse("2026-10-14T01:29:00Z")); // 22:29 BA, ventana 22:31 a 22:59

        assertThat(service.findBookable(COMMON, "33", null)).isEmpty();
        assertThat(repository.count()).isZero();
    }
}
