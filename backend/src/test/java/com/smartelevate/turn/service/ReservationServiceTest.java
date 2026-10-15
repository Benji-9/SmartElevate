package com.smartelevate.turn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import com.smartelevate.priority.model.PriorityCategory;
import com.smartelevate.priority.service.PriorityService;
import com.smartelevate.turn.dto.ReservationPageResponse;
import com.smartelevate.turn.dto.ReservationResponse;
import com.smartelevate.turn.dto.ReserveRequest;
import com.smartelevate.turn.model.CheckIn;
import com.smartelevate.turn.model.CheckInOutcome;
import com.smartelevate.turn.model.CheckInSource;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.turn.model.ReservationStatus;
import com.smartelevate.turn.repository.CheckInRepository;
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
import org.springframework.web.server.ResponseStatusException;

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
    @Mock
    private PriorityService priorities;
    @Mock
    private NoShowService noShows;
    @Mock
    private CheckInRepository checkIns;

    private ReservationService service;
    private Elevator elevator;

    @BeforeEach
    void setUp() {
        CampusTime time = new CampusTime(Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("America/Argentina/Buenos_Aires"));
        service = new ReservationService(reservations, departures, users, config, time, priorities, noShows, checkIns);
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
        lenient().when(config.cancellationDeadlineBefore()).thenReturn(Duration.ofMinutes(1));
        lenient().when(config.checkInClosesAfter()).thenReturn(Duration.ofMinutes(2));
        lenient().when(config.prioritySeatsPerDeparture()).thenReturn(2);
        lenient().when(config.prioritySeatsReleaseBefore()).thenReturn(Duration.ofMinutes(3));
        lenient().when(priorities.currentPriority(USER)).thenReturn(PriorityCategory.NONE);
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
        given(priorities.currentPriority(USER)).willReturn(PriorityCategory.TEACHER);

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

    // --- suspensión por faltas (#231) ---

    @Test
    void aSuspendedUserCannotReserveUntilTheSuspensionEnds() {
        departureAt(NOW.plus(Duration.ofMinutes(10)));
        // 2026-10-14 12:00 en Buenos Aires.
        given(noShows.suspendedUntil(USER, NOW)).willReturn(Optional.of(Instant.parse("2026-10-14T15:00:00Z")));

        assertThatThrownBy(() -> reserve(0, 8))
                .isInstanceOfSatisfying(ReservationRejectedException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(ReservationRejectedException.Reason.NO_SHOW_SUSPENDED);
                    assertThat(e.getMessage()).contains("14/10 a las 12:00");
                });
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test
    void aPriorityUserIsNeverSuspended() {
        departureAt(NOW.plus(Duration.ofMinutes(10)));
        given(priorities.currentPriority(USER)).willReturn(PriorityCategory.REDUCED_MOBILITY);

        assertThatCode(() -> reserve(0, 8)).doesNotThrowAnyException();
        verify(noShows, never()).suspendedUntil(any(), any());
    }

    @Test
    void aDepartureOnAnotherDayIsOutsideTheWindowEvenIfInsideTheOffsets() {
        // 23:50 en Buenos Aires: faltan 20 min para la salida de las 00:10 del día siguiente.
        CampusTime late = new CampusTime(Clock.fixed(Instant.parse("2026-10-14T02:50:00Z"), ZoneOffset.UTC),
                ZoneId.of("America/Argentina/Buenos_Aires"));
        service = new ReservationService(reservations, departures, users, config, late, priorities, noShows, checkIns);
        departureAt(Instant.parse("2026-10-14T03:10:00Z"));

        assertRejected(() -> reserve(0, 8), ReservationRejectedException.Reason.OUTSIDE_BOOKING_WINDOW);
    }

    // --- ascensores dedicados: 05 docentes, 06 movilidad reducida ---

    @ParameterizedTest
    @CsvSource({
            "TEACHERS, NONE, false", "TEACHERS, TEACHER, true", "TEACHERS, REDUCED_MOBILITY, false",
            "REDUCED_MOBILITY, NONE, false", "REDUCED_MOBILITY, TEACHER, false",
            "REDUCED_MOBILITY, REDUCED_MOBILITY, true",
            "COMMON, NONE, true", "COMMON, TEACHER, true", "COMMON, REDUCED_MOBILITY, true"})
    void dedicatedElevatorsOnlyAdmitTheirCategory(ElevatorUsage usage, PriorityCategory priority, boolean allowed) {
        elevator = new Elevator("05", elevator.getCore(), usage, true, elevator.getFloors());
        departureAt(NOW.plus(Duration.ofMinutes(10)));
        given(priorities.currentPriority(USER)).willReturn(priority);

        if (allowed) {
            assertThat(reserve(0, 8).seatNumber()).isEqualTo(1);
        } else {
            assertRejected(() -> reserve(0, 8), ReservationRejectedException.Reason.ELEVATOR_NOT_BOOKABLE);
        }
    }

    @Test
    void dedicatedElevatorDoesNotHoldPrioritySeatsAndMarksTheReservation() {
        elevator = new Elevator("05", elevator.getCore(), ElevatorUsage.TEACHERS, true, elevator.getFloors());
        departureAt(NOW.plus(Duration.ofMinutes(10)));
        given(priorities.currentPriority(USER)).willReturn(PriorityCategory.TEACHER);
        seatsTaken(9);

        assertThat(reserve(0, 8).seatNumber()).isEqualTo(10);
        verify(reservations).saveAndFlush(org.mockito.ArgumentMatchers.argThat(Reservation::isPriorityUser));
    }

    // --- lugares reservados para prioritarios: 2 de 10, liberados 3 min antes ---

    private void prioritySeatsTaken(long count) {
        lenient().when(reservations.countActivePriority(DEPARTURE_ID)).thenReturn(count);
    }

    @Test
    void withEightCommonBookedACommonUserCannotTakeTheNinthSeat() {
        departureAt(NOW.plus(Duration.ofMinutes(10)));
        seatsTaken(8);
        prioritySeatsTaken(0);

        assertThatThrownBy(() -> reserve(0, 8)).isInstanceOf(TurnFullException.class)
                .hasMessageContaining("prioritarios");
        verify(reservations, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @CsvSource({"TEACHER, 9", "REDUCED_MOBILITY, 9"})
    void withEightCommonBookedAPriorityUserTakesTheNinthSeat(PriorityCategory priority, int seat) {
        departureAt(NOW.plus(Duration.ofMinutes(10)));
        seatsTaken(8);
        prioritySeatsTaken(0);
        given(priorities.currentPriority(USER)).willReturn(priority);

        assertThat(reserve(0, 8).seatNumber()).isEqualTo(seat);
        verify(reservations).saveAndFlush(org.mockito.ArgumentMatchers.argThat(Reservation::isPriorityUser));
    }

    @Test
    void sevenCommonPlusOnePriorityStillLeaveOneSeatForACommonUser() {
        departureAt(NOW.plus(Duration.ofMinutes(10)));
        seatsTaken(8);
        prioritySeatsTaken(1);

        assertThat(reserve(0, 8).seatNumber()).isEqualTo(9);
        verify(reservations).saveAndFlush(org.mockito.ArgumentMatchers.argThat(r -> !r.isPriorityUser()));
    }

    @Test
    void eightCommonPlusOnePriorityKeepTheLastSeatForAPriorityUser() {
        departureAt(NOW.plus(Duration.ofMinutes(10)));
        seatsTaken(9);
        prioritySeatsTaken(1);

        assertThatThrownBy(() -> reserve(0, 8)).isInstanceOf(TurnFullException.class);
    }

    @Test
    void priorityUsersBeyondTheReservedSeatsCountAgainstTheCommonOnes() {
        // 3 prioritarios + 6 comunes: los reservados ya están cubiertos, queda 1 para cualquiera.
        departureAt(NOW.plus(Duration.ofMinutes(10)));
        seatsTaken(9);
        prioritySeatsTaken(3);

        assertThat(reserve(0, 8).seatNumber()).isEqualTo(10);
    }

    @ParameterizedTest
    @CsvSource({"PT3M1S, false", "PT3M, true", "PT2M30S, true"})
    void reservedSeatsAreReleasedThreeMinutesBefore(String untilDeparture, boolean released) {
        departureAt(NOW.plus(Duration.parse(untilDeparture)));
        seatsTaken(8);
        prioritySeatsTaken(0);

        if (released) {
            assertThat(reserve(0, 8).seatNumber()).isEqualTo(9);
        } else {
            assertThatThrownBy(() -> reserve(0, 8)).isInstanceOf(TurnFullException.class);
        }
    }

    @Test
    void zeroPrioritySeatsDisablesTheRule() {
        given(config.prioritySeatsPerDeparture()).willReturn(0);
        departureAt(NOW.plus(Duration.ofMinutes(10)));
        seatsTaken(9);

        assertThat(reserve(0, 8).seatNumber()).isEqualTo(10);
    }

    @Test
    void fullDepartureIsFullForPriorityUsersToo() {
        departureAt(NOW.plus(Duration.ofMinutes(10)));
        seatsTaken(10);
        given(priorities.currentPriority(USER)).willReturn(PriorityCategory.REDUCED_MOBILITY);

        assertThatThrownBy(() -> reserve(0, 8)).isInstanceOf(TurnFullException.class)
                .hasMessageNotContaining("prioritarios");
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

    @ParameterizedTest
    @CsvSource({"REDUCED_MOBILITY, true", "TEACHER, false", "NONE, false"})
    void onlyReducedMobilityIsExemptFromTheLowFloorsRule(PriorityCategory priority, boolean allowed) {
        departureAt(NOW.plus(Duration.ofMinutes(10)));
        given(priorities.currentPriority(USER)).willReturn(priority);

        if (allowed) {
            assertThatCode(() -> reserve(0, 2)).doesNotThrowAnyException();
        } else {
            assertRejected(() -> reserve(0, 2), ReservationRejectedException.Reason.LOW_FLOOR_DESTINATION);
        }
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

    // --- Cancelar (#116) ---

    private Reservation reservationOf(UUID owner, Instant departureAt) {
        Departure d = new Departure(elevator, departureAt, 10);
        ReflectionTestUtils.setField(d, "id", DEPARTURE_ID);
        Reservation r = new Reservation(owner, d, 3, 0, 8, NOW.minus(Duration.ofMinutes(10)));
        ReflectionTestUtils.setField(r, "id", 55L);
        lenient().when(reservations.findById(55L)).thenReturn(Optional.of(r));
        lenient().when(config.cancellationDeadlineBefore()).thenReturn(Duration.ofMinutes(1));
        return r;
    }

    @ParameterizedTest
    @CsvSource({"61, CANCELLED", "60, CANCELLED", "59, CANCELLED_LATE", "-30, CANCELLED_LATE"})
    void cancelBeforeTheDeadlineIsFreeAndAfterItIsLate(long secondsBefore, ReservationStatus expected) {
        Reservation r = reservationOf(USER, NOW.plusSeconds(secondsBefore));

        service.cancel(USER, 55L);

        assertThat(r.getStatus()).isEqualTo(expected);
        assertThat(r.getSeatNumber()).isNull();
        verify(users).lockById(USER);
        verify(departures).findByIdForUpdate(DEPARTURE_ID);
    }

    @Test
    void cancellingTwiceKeepsTheFirstOutcome() {
        Reservation r = reservationOf(USER, NOW.plusSeconds(61));
        service.cancel(USER, 55L);
        given(config.cancellationDeadlineBefore()).willReturn(Duration.ofHours(1));

        assertThatCode(() -> service.cancel(USER, 55L)).doesNotThrowAnyException();

        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void someoneElsesReservationLooksLikeItDoesNotExist() {
        Reservation r = reservationOf(UUID.randomUUID(), NOW.plusSeconds(600));
        assertRejected(() -> service.cancel(USER, 55L), ReservationRejectedException.Reason.RESERVATION_NOT_FOUND);
        assertRejected(() -> service.cancel(USER, 99L), ReservationRejectedException.Reason.RESERVATION_NOT_FOUND);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(r.getSeatNumber()).isEqualTo(3);
    }

    // --- Activa, detalle e historial (#214) ---

    @Test
    void activeLooksBackUntilTheCheckInWindowCloses() {
        Reservation r = reservationOf(USER, NOW.minusSeconds(30));
        // check_in_closes_after = 2 min: sigue activa hasta 2 min después de la salida.
        given(reservations.findActiveFrom(USER, NOW.minus(Duration.ofMinutes(2)))).willReturn(List.of(r));

        assertThat(service.active(USER)).hasValueSatisfying(res -> {
            assertThat(res.id()).isEqualTo(55L);
            assertThat(res.elevator()).isEqualTo("33");
            assertThat(res.coreName()).isEqualTo("Independencia 2");
            assertThat(res.buildingName()).isEqualTo("Independencia");
            assertThat(res.status()).isEqualTo(ReservationStatus.ACTIVE);
            assertThat(res.cancelCountsAsNoShow()).isTrue();
            assertThat(res.checkInOutcome()).isNull();
        });
    }

    @Test
    void noActiveReservationIsEmpty() {
        assertThat(service.active(USER)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"61, false", "60, false", "59, true"})
    void cancelCountsAsNoShowPastTheDeadline(long secondsBefore, boolean countsAsNoShow) {
        reservationOf(USER, NOW.plusSeconds(secondsBefore));

        assertThat(service.get(USER, 55L).cancelCountsAsNoShow()).isEqualTo(countsAsNoShow);
    }

    @Test
    void aClosedReservationNeverCountsCancellingAsNoShowAndBringsItsCheckIn() {
        Reservation r = reservationOf(USER, NOW.minusSeconds(600));
        r.markUsed();
        given(checkIns.findByReservationId(55L)).willReturn(Optional.of(checkIn(55L, CheckInOutcome.OTHER_ELEVATOR)));

        ReservationResponse res = service.get(USER, 55L);

        assertThat(res.status()).isEqualTo(ReservationStatus.USED);
        assertThat(res.cancelCountsAsNoShow()).isFalse();
        assertThat(res.checkInOutcome()).isEqualTo(CheckInOutcome.OTHER_ELEVATOR);
    }

    @Test
    void getOnlyShowsMyOwnReservations() {
        reservationOf(UUID.randomUUID(), NOW.plusSeconds(600));

        assertRejected(() -> service.get(USER, 55L), ReservationRejectedException.Reason.RESERVATION_NOT_FOUND);
        assertRejected(() -> service.get(USER, 99L), ReservationRejectedException.Reason.RESERVATION_NOT_FOUND);
    }

    private List<Reservation> history(int count) {
        return IntStream.range(0, count).mapToObj(i -> {
            Departure d = new Departure(elevator, NOW.minus(Duration.ofMinutes(i)), 10);
            ReflectionTestUtils.setField(d, "id", 500L - i);
            Reservation r = new Reservation(USER, d, 1, 0, 8, NOW);
            ReflectionTestUtils.setField(r, "id", 1000L - i);
            return r;
        }).toList();
    }

    @Test
    void historyPageWithOneMoreThanThePageSizeHasANextCursor() {
        int size = ReservationService.HISTORY_PAGE_SIZE;
        List<Reservation> found = history(size + 1);
        given(reservations.history(eq(USER), any())).willReturn(found);
        given(checkIns.findByReservationIdIn(any())).willReturn(List.of(checkIn(1000L, CheckInOutcome.LATE)));

        ReservationPageResponse page = service.history(USER, null);

        assertThat(page.items()).hasSize(size);
        assertThat(page.items().getFirst().checkInOutcome()).isEqualTo(CheckInOutcome.LATE);
        assertThat(page.items().get(1).checkInOutcome()).isNull();
        Reservation last = found.get(size - 1);
        assertThat(page.nextCursor()).isEqualTo(last.getDeparture().getDepartureAt() + "_" + last.getId());
    }

    @Test
    void lastHistoryPageHasNoCursorAndTheCursorPicksUpWhereItLeft() {
        given(reservations.historyBefore(eq(USER), eq(Instant.parse("2026-10-13T15:00:00Z")), eq(981L), any()))
                .willReturn(history(ReservationService.HISTORY_PAGE_SIZE));

        ReservationPageResponse page = service.history(USER, "2026-10-13T15:00:00Z_981");

        assertThat(page.items()).hasSize(ReservationService.HISTORY_PAGE_SIZE);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void emptyHistoryDoesNotLookForCheckIns() {
        ReservationPageResponse page = service.history(USER, "");

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        verify(checkIns, never()).findByReservationIdIn(any());
    }

    @ParameterizedTest
    @CsvSource({"basura", "2026-10-13T15:00:00Z_x", "x_55", "_"})
    void anInvalidCursorIsABadRequest(String cursor) {
        assertThatThrownBy(() -> service.history(USER, cursor))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        verify(reservations, never()).historyBefore(any(), any(), anyLong(), any());
    }

    private static CheckIn checkIn(long reservationId, CheckInOutcome outcome) {
        return new CheckIn(reservationId, outcome, "33", CheckInSource.REAL, UUID.randomUUID(), NOW);
    }
}
