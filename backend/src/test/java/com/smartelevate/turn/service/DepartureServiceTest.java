package com.smartelevate.turn.service;

import static org.assertj.core.api.Assertions.assertThat;

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
    private static final AtomicReference<Instant> NOW = new AtomicReference<>();

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

    @Test
    void tuesdayAt1255ReturnsTheExpectedSlotsInUtc() {
        NOW.set(Instant.parse("2026-10-13T15:55:00Z")); // 12:55 BA

        List<DepartureResponse> result = service.findBookable("33", null);

        // Ventana 12:57 a 13:25 BA, salidas cada 2 min desde las 07:00: 12:58 ... 13:24 = 14 salidas.
        assertThat(result).hasSize(14);
        assertThat(result.getFirst().departureAt()).isEqualTo(Instant.parse("2026-10-13T15:58:00Z"));
        assertThat(result.getLast().departureAt()).isEqualTo(Instant.parse("2026-10-13T16:24:00Z"));
        assertThat(result).allSatisfy(d -> {
            assertThat(d.capacity()).isEqualTo(10);
            assertThat(d.remainingSeats()).isEqualTo(10);
            assertThat(d.elevator()).isEqualTo("33");
            assertThat(d.building()).isEqualTo("INDEPENDENCIA");
        });
    }

    @Test
    void remainingSeatsDiscountsActiveReservations() {
        NOW.set(Instant.parse("2026-10-13T15:55:00Z"));
        Departure first = repositoryDeparture(service.findBookable("33", null).getFirst().id());
        for (int seat = 1; seat <= 3; seat++) {
            UUID id = users.save(new User("P" + seat, "p" + seat + "@uade.edu.ar", "20" + seat, "x",
                    DeclaredUserType.STUDENT, NOW.get())).getId();
            reservations.save(new Reservation(id, first, seat, 0, 8, NOW.get()));
        }

        List<DepartureResponse> result = service.findBookable("33", null);

        assertThat(result.getFirst().remainingSeats()).isEqualTo(7);
        assertThat(result.get(1).remainingSeats()).isEqualTo(10);
    }

    private Departure repositoryDeparture(long id) {
        return repository.findById(id).orElseThrow();
    }

    @Test
    void generationIsIdempotent() {
        NOW.set(Instant.parse("2026-10-13T15:55:00Z"));

        service.findBookable("33", null);
        long first = repository.count();
        List<DepartureResponse> again = service.findBookable("33", null);

        assertThat(again).hasSize(14);
        assertThat(repository.count()).isEqualTo(first).isEqualTo(14);
    }

    @Test
    void excludesDedicatedElevatorsAndFiltersByBuilding() {
        NOW.set(Instant.parse("2026-10-13T15:55:00Z"));

        assertThat(service.findBookable("05", null)).isEmpty(); // docentes
        assertThat(service.findBookable("06", null)).isEmpty(); // movilidad reducida
        List<DepartureResponse> independencia = service.findBookable(null, "independencia");
        assertThat(independencia).extracting(DepartureResponse::elevator).containsOnly("33", "34");
        assertThat(independencia).hasSize(28);
    }

    @Test
    void doesNotGenerateBeforeOperatingStart() {
        NOW.set(Instant.parse("2026-10-13T09:40:00Z")); // 06:40 BA, ventana 06:42 a 07:10

        List<DepartureResponse> result = service.findBookable("33", null);

        assertThat(result).hasSize(6); // 07:00 a 07:10
        assertThat(result.getFirst().departureAt()).isEqualTo(Instant.parse("2026-10-13T10:00:00Z"));
    }

    @Test
    void doesNotGenerateAtOrAfterOperatingEndEvenPastUtcMidnight() {
        NOW.set(Instant.parse("2026-10-14T01:20:00Z")); // 22:20 BA, ya es mañana en UTC

        List<DepartureResponse> result = service.findBookable("33", null);

        // Ventana 22:22 a 22:50: solo hasta las 22:28 (el horario es [07:00, 22:30)).
        assertThat(result).hasSize(4);
        assertThat(result.getLast().departureAt()).isEqualTo(Instant.parse("2026-10-14T01:28:00Z"));
    }

    @Test
    void returnsNothingWhenTheWindowIsOutsideOperatingHours() {
        NOW.set(Instant.parse("2026-10-14T01:29:00Z")); // 22:29 BA, ventana 22:31 a 22:59

        assertThat(service.findBookable("33", null)).isEmpty();
        assertThat(repository.count()).isZero();
    }
}
