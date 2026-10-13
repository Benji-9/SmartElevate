package com.smartelevate.turn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.smartelevate.common.config.ConfigService;
import com.smartelevate.common.time.CampusTime;
import com.smartelevate.elevator.model.Building;
import com.smartelevate.elevator.model.Core;
import com.smartelevate.elevator.model.Elevator;
import com.smartelevate.elevator.model.ElevatorUsage;
import com.smartelevate.turn.dto.ReservationResponse;
import com.smartelevate.turn.dto.ReserveRequest;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.turn.repository.DepartureRepository;
import com.smartelevate.turn.repository.ReservationRepository;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    // Martes 2026-10-13 12:55 en Buenos Aires (UTC-3). La ventana va de 12:57 a 13:25.
    private static final Instant NOW = Instant.parse("2026-10-13T15:55:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final long DEPARTURE_ID = 7;

    @Mock
    private ReservationRepository reservations;
    @Mock
    private DepartureRepository departures;
    @Mock
    private UserRepository users;
    @Mock
    private ConfigService config;

    private ReservationService service;
    private Elevator elevator;

    @BeforeEach
    void setUp() {
        CampusTime time = new CampusTime(Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("America/Argentina/Buenos_Aires"));
        service = new ReservationService(reservations, departures, users, config, time);
        // Ascensor 33 de Independencia: -4 a 11 sin el 1.
        Building building = new Building("INDEPENDENCIA", "Independencia", -4, 11);
        Core core = new Core("IND2", building, "Independencia 2");
        Set<Integer> floors = new java.util.HashSet<>(IntStream.rangeClosed(-4, 11).boxed().toList());
        floors.remove(1);
        elevator = new Elevator("33", core, ElevatorUsage.COMMON, true, floors);

        lenient().when(users.lockById(USER)).thenReturn(Optional.of(org.mockito.Mockito.mock(User.class)));
        lenient().when(config.bookingClosesBefore()).thenReturn(Duration.ofMinutes(2));
        lenient().when(config.bookingOpensBefore()).thenReturn(Duration.ofMinutes(30));
        lenient().when(config.maxActiveBookingsPerUser()).thenReturn(1);
        lenient().when(config.maxDailyBookingsPerUser()).thenReturn(8);
        lenient().when(config.lowFloorsThreshold()).thenReturn(4);
        lenient().when(reservations.saveAndFlush(any(Reservation.class))).thenAnswer(i -> {
            Reservation r = i.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 55L);
            return r;
        });
    }

    private Departure departureAt(Instant at) {
        Departure d = new Departure(elevator, at, 10);
        ReflectionTestUtils.setField(d, "id", DEPARTURE_ID);
        given(departures.findByIdForUpdate(DEPARTURE_ID)).willReturn(Optional.of(d));
        return d;
    }

    private ReservationResponse reserve(int origin, int destination) {
        return service.reserve(USER, new ReserveRequest(DEPARTURE_ID, origin, destination));
    }

    private void seatsTaken(int count) {
        given(reservations.activeSeats(DEPARTURE_ID)).willReturn(IntStream.rangeClosed(1, count).boxed().toList());
    }

    @Test
    void firstReservationGetsSeatOneAndTheUserComesFromThePrincipal() {
        departureAt(NOW.plus(Duration.ofMinutes(10)));

        ReservationResponse response = reserve(0, 8);

        assertThat(response.seatNumber()).isEqualTo(1);
        assertThat(response.elevator()).isEqualTo("33");
        assertThat(response.originFloor()).isZero();
        assertThat(response.destinationFloor()).isEqualTo(8);
        assertThat(response.createdAt()).isEqualTo(NOW);
    }

    @Test
    void takesTheLowestFreeSeat() {
        departureAt(NOW.plus(Duration.ofMinutes(10)));
        given(reservations.activeSeats(DEPARTURE_ID)).willReturn(List.of(1, 3, 4));

        assertThat(reserve(0, 8).seatNumber()).isEqualTo(2);
    }

    @Test
    void departureWithNineBookingsStillHasTheTenthSeat() {
        departureAt(NOW.plus(Duration.ofMinutes(10)));
        seatsTaken(9);

        assertThat(reserve(0, 8).seatNumber()).isEqualTo(10);
    }

    @Test
    void departureWithTenBookingsIsFull() {
        departureAt(NOW.plus(Duration.ofMinutes(10)));
        seatsTaken(10);

        assertThatThrownBy(() -> reserve(0, 8)).isInstanceOf(TurnFullException.class);
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test
    void departureWithElevenBookingsIsFull() {
        departureAt(NOW.plus(Duration.ofMinutes(10)));
        seatsTaken(11);

        assertThatThrownBy(() -> reserve(0, 8)).isInstanceOf(TurnFullException.class);
    }

    @Test
    void unknownDepartureIsRejected() {
        given(departures.findByIdForUpdate(anyLong())).willReturn(Optional.empty());

        assertRejected(() -> reserve(0, 8), ReservationRejectedException.Reason.DEPARTURE_NOT_FOUND);
    }

    // --- ventana de reserva: abre 30:00 antes, cierra 2:00 antes, mismo día ---

    @ParameterizedTest
    @CsvSource({
            "PT2M, true", "PT2M1S, true", "PT1M59S, false", "PT0S, false",
            "PT30M, true", "PT29M59S, true", "PT30M1S, false"})
    void windowBoundaries(String untilDeparture, boolean allowed) {
        departureAt(NOW.plus(Duration.parse(untilDeparture)));

        if (allowed) {
            assertThatCode(() -> reserve(0, 8)).doesNotThrowAnyException();
        } else {
            assertRejected(() -> reserve(0, 8), ReservationRejectedException.Reason.OUTSIDE_BOOKING_WINDOW);
        }
    }

    @Test
    void aDepartureOnAnotherDayIsOutsideTheWindowEvenIfInsideTheOffsets() {
        // 23:50 en Buenos Aires: faltan 20 min para la salida de las 00:10 del día siguiente.
        CampusTime late = new CampusTime(Clock.fixed(Instant.parse("2026-10-14T02:50:00Z"), ZoneOffset.UTC),
                ZoneId.of("America/Argentina/Buenos_Aires"));
        service = new ReservationService(reservations, departures, users, config, late);
        departureAt(Instant.parse("2026-10-14T03:10:00Z"));

        assertRejected(() -> reserve(0, 8), ReservationRejectedException.Reason.OUTSIDE_BOOKING_WINDOW);
    }

    @Test
    void dedicatedElevatorIsNotBookableYet() {
        elevator = new Elevator("05", elevator.getCore(), ElevatorUsage.TEACHERS, true, elevator.getFloors());
        departureAt(NOW.plus(Duration.ofMinutes(10)));

        assertRejected(() -> reserve(0, 8), ReservationRejectedException.Reason.ELEVATOR_NOT_BOOKABLE);
    }

    // --- límites por usuario ---

    @Test
    void oneOngoingReservationBlocksAnotherOne() {
        departureAt(NOW.plus(Duration.ofMinutes(10)));
        given(reservations.countOngoing(USER, NOW)).willReturn(1L);

        assertRejected(() -> reserve(0, 8), ReservationRejectedException.Reason.ACTIVE_LIMIT_REACHED);
    }

    @Test
    void dailyLimitRejectsTheEighthExistingButNotTheSeventh() {
        departureAt(NOW.plus(Duration.ofMinutes(10)));
        // Día de Buenos Aires: 03:00Z a 03:00Z del día siguiente.
        Instant from = Instant.parse("2026-10-13T03:00:00Z");
        Instant to = Instant.parse("2026-10-14T03:00:00Z");
        given(reservations.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(USER, from, to))
                .willReturn(7L, 8L);

        assertThatCode(() -> reserve(0, 8)).doesNotThrowAnyException();
        assertRejected(() -> reserve(0, 8), ReservationRejectedException.Reason.DAILY_LIMIT_REACHED);
    }

    // --- elegibilidad de pisos ---

    @ParameterizedTest
    @CsvSource({
            "5, 5, SAME_FLOOR",
            "0, 12, FLOOR_OUT_OF_RANGE",
            "-5, 3, FLOOR_OUT_OF_RANGE",
            "1, 8, FLOOR_NOT_SERVED",
            "0, 1, FLOOR_NOT_SERVED",
            "0, 2, LOW_FLOOR_DESTINATION",
            "0, 4, LOW_FLOOR_DESTINATION"})
    void invalidFloors(int origin, int destination, ReservationRejectedException.Reason reason) {
        departureAt(NOW.plus(Duration.ofMinutes(10)));

        assertRejected(() -> reserve(origin, destination), reason);
    }

    @ParameterizedTest
    @CsvSource({"0, 5", "8, 0", "-4, 11", "6, -2", "3, 5"})
    void validFloors(int origin, int destination) {
        departureAt(NOW.plus(Duration.ofMinutes(10)));

        assertThatCode(() -> reserve(origin, destination)).doesNotThrowAnyException();
    }

    @Test
    void lowFloorsThresholdZeroDisablesTheRule() {
        given(config.lowFloorsThreshold()).willReturn(0);
        departureAt(NOW.plus(Duration.ofMinutes(10)));

        assertThatCode(() -> reserve(0, 2)).doesNotThrowAnyException();
    }

    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                                       ReservationRejectedException.Reason reason) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ReservationRejectedException.class,
                e -> assertThat(e.getReason()).isEqualTo(reason));
    }
}
