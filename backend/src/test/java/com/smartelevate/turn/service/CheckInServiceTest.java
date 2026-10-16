package com.smartelevate.turn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.smartelevate.elevator.service.InvalidQrTokenException;
import com.smartelevate.elevator.service.QrTokenService;
import com.smartelevate.elevator.service.QrTokenService.QrTokenClaims;
import com.smartelevate.priority.model.PriorityAudit;
import com.smartelevate.priority.repository.PriorityAuditRepository;
import com.smartelevate.turn.dto.CheckInResponse;
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
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {

    // Martes 2026-10-13 12:55 en Buenos Aires.
    private static final Instant NOW = Instant.parse("2026-10-13T15:55:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID KIOSK = UUID.randomUUID();
    private static final String TOKEN = "token";

    @Mock
    private QrTokenService qrTokens;
    @Mock
    private ReservationRepository reservations;
    @Mock
    private CheckInRepository checkIns;
    @Mock
    private UserRepository users;
    @Mock
    private PriorityAuditRepository audit;
    @Mock
    private ConfigService config;
    @Mock
    private DepartureRepository departures;
    @Mock
    private EntityManager entityManager;

    private CheckInService service;
    private Elevator e33;
    private final List<Reservation> mine = new ArrayList<>();

    @BeforeEach
    void setUp() {
        CampusTime time = new CampusTime(Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("America/Argentina/Buenos_Aires"));
        service = new CheckInService(qrTokens, reservations, checkIns, users, audit, config, time, departures, entityManager);
        Core core = new Core("IND2", new Building("INDEPENDENCIA", "Independencia", -4, 11), "Independencia 2");
        e33 = new Elevator("33", core, ElevatorUsage.COMMON, true, Set.of(0, 8));
        lenient().when(users.lockById(USER)).thenReturn(Optional.of(org.mockito.Mockito.mock(User.class)));
        lenient().when(config.checkInOpensBefore()).thenReturn(Duration.ofMinutes(1));
        lenient().when(config.checkInClosesAfter()).thenReturn(Duration.ofMinutes(2));
        lenient().when(config.noShowAfter()).thenReturn(Duration.ofSeconds(60));
        lenient().when(reservations.findMine(any(), any())).thenReturn(mine);
        scanned("33", false);
    }

    private void scanned(String elevator, boolean simulated) {
        lenient().when(qrTokens.validate(TOKEN)).thenReturn(new QrTokenClaims(elevator, KIOSK, simulated));
    }

    /** Reserva del usuario cuya salida fue hace {@code secondsAgo} (negativo = todavía no salió). */
    private Reservation reservation(long id, long secondsAgo) {
        Departure departure = new Departure(e33, NOW.minusSeconds(secondsAgo), 10);
        ReflectionTestUtils.setField(departure, "id", 7L);
        Reservation r = new Reservation(USER, departure, 3, 0, 8, NOW.minus(Duration.ofMinutes(20)));
        ReflectionTestUtils.setField(r, "id", id);
        mine.add(r);
        return r;
    }

    private CheckIn savedCheckIn() {
        ArgumentCaptor<CheckIn> captor = ArgumentCaptor.forClass(CheckIn.class);
        verify(checkIns).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private void rejected(ReservationRejectedException.Reason reason) {
        assertThatThrownBy(() -> service.checkIn(USER, TOKEN))
                .isInstanceOfSatisfying(ReservationRejectedException.class, e -> assertThat(e.getReason())
                        .isEqualTo(reason));
        verify(checkIns, never()).saveAndFlush(any());
    }

    @ParameterizedTest(name = "a {0} s de la salida: {1}")
    @CsvSource({
            "-60, ON_TIME, USED",
            "0, ON_TIME, USED",
            "60, ON_TIME, USED",
            "61, LATE, NO_SHOW",
            "120, LATE, NO_SHOW"})
    void outcomeDependsOnTheTimeAroundTheDeparture(long secondsAfter, CheckInOutcome outcome,
                                                   ReservationStatus status) {
        Reservation r = reservation(55, secondsAfter);

        CheckInResponse response = service.checkIn(USER, TOKEN);

        assertThat(response.outcome()).isEqualTo(outcome);
        assertThat(response.reservationStatus()).isEqualTo(status);
        assertThat(r.getStatus()).isEqualTo(status);
        assertThat(r.getSeatNumber()).isEqualTo(3); // USED y NO_SHOW conservan el lugar
        CheckIn saved = savedCheckIn();
        assertThat(saved.getReservationId()).isEqualTo(55);
        assertThat(saved.getOutcome()).isEqualTo(outcome);
        assertThat(saved.getCheckedInAt()).isEqualTo(NOW);
        assertThat(saved.getKioskId()).isEqualTo(KIOSK);
        assertThat(saved.getSource()).isEqualTo(CheckInSource.REAL);
        verify(audit, never()).save(any());
    }

    @Test
    void anOutageCommittedWhileWaitingForTheDepartureLockRejectsTheCheckIn() {
        // Leída ACTIVE antes del lock; al tomar la salida el ascensor ya la canceló (#134): no se pisa con USED.
        Reservation r = reservation(55, 0);
        org.mockito.Mockito.doAnswer(inv -> {
            r.cancelByOutage();
            return null;
        }).when(entityManager).refresh(r);

        rejected(ReservationRejectedException.Reason.NO_ACTIVE_RESERVATION);

        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(users, departures, entityManager);
        order.verify(users).lockById(USER);
        order.verify(departures).findByIdForUpdate(7L);
        order.verify(entityManager).refresh(r);
    }

    @ParameterizedTest(name = "a {0} s de la salida se rechaza")
    @CsvSource({"-61", "121", "-600"})
    void outsideTheWindowIsRejected(long secondsAfter) {
        Reservation r = reservation(55, secondsAfter);

        rejected(ReservationRejectedException.Reason.OUTSIDE_CHECK_IN_WINDOW);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    void anotherElevatorWithinTimeIsUsedAndKeepsBothElevators() {
        scanned("34", false);
        Reservation r = reservation(55, 10);

        CheckInResponse response = service.checkIn(USER, TOKEN);

        assertThat(response.outcome()).isEqualTo(CheckInOutcome.OTHER_ELEVATOR);
        assertThat(response.elevator()).isEqualTo("34");
        assertThat(response.reservedElevator()).isEqualTo("33");
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.USED);
        assertThat(savedCheckIn().getElevatorCode()).isEqualTo("34");
    }

    @Test
    void anotherElevatorLateIsLate() {
        scanned("34", false);
        reservation(55, 90);

        assertThat(service.checkIn(USER, TOKEN).outcome()).isEqualTo(CheckInOutcome.LATE);
        assertThat(savedCheckIn().getElevatorCode()).isEqualTo("34");
    }

    @Test
    void aLateScanAfterTheNoShowJobKeepsNoShowAndRecordsTheVisit() {
        Reservation r = reservation(55, 90);
        r.markNoShow();

        CheckInResponse response = service.checkIn(USER, TOKEN);

        assertThat(response.outcome()).isEqualTo(CheckInOutcome.LATE);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
        assertThat(savedCheckIn().getOutcome()).isEqualTo(CheckInOutcome.LATE);
    }

    @Test
    void withoutReservationIsRejected() {
        rejected(ReservationRejectedException.Reason.NO_ACTIVE_RESERVATION);
    }

    @Test
    void cancelledReservationsDoNotCount() {
        reservation(55, 0).cancel(NOW.minus(Duration.ofMinutes(15)), Duration.ofMinutes(10));

        rejected(ReservationRejectedException.Reason.NO_ACTIVE_RESERVATION);
    }

    @Test
    void secondReadOfTheSameReservationIsRejected() {
        reservation(55, 0).markUsed();

        rejected(ReservationRejectedException.Reason.ALREADY_CHECKED_IN);
    }

    @Test
    void secondLateReadIsRejectedToo() {
        reservation(55, 90).markNoShow();
        given(checkIns.existsByReservationId(55)).willReturn(true);

        rejected(ReservationRejectedException.Reason.ALREADY_CHECKED_IN);
    }

    @Test
    void whenTwoReservationsFallInTheWindowTheNextOneWins() {
        // findMine viene con la salida más tardía primero.
        Reservation next = reservation(56, -30);
        Reservation previous = reservation(55, 100);

        assertThat(service.checkIn(USER, TOKEN).reservationId()).isEqualTo(56);
        assertThat(next.getStatus()).isEqualTo(ReservationStatus.USED);
        assertThat(previous.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    void invalidTokenIsRejectedBeforeLookingForTheReservation() {
        given(qrTokens.validate(TOKEN)).willThrow(new InvalidQrTokenException());

        assertThatThrownBy(() -> service.checkIn(USER, TOKEN)).isInstanceOf(InvalidQrTokenException.class);
        verify(reservations, never()).findMine(any(), any());
        verify(checkIns, never()).saveAndFlush(any());
    }

    @Test
    void simulatedKioskMarksTheSourceAndIsAudited() {
        scanned("33", true);
        reservation(55, 0);

        assertThat(service.checkIn(USER, TOKEN).source()).isEqualTo(CheckInSource.SIMULATED);

        assertThat(savedCheckIn().getSource()).isEqualTo(CheckInSource.SIMULATED);
        ArgumentCaptor<PriorityAudit> captor = ArgumentCaptor.forClass(PriorityAudit.class);
        verify(audit).save(captor.capture());
        assertThat(captor.getValue().getActorId()).isEqualTo(USER);
        assertThat(captor.getValue().getTargetKioskId()).isEqualTo(KIOSK);
        assertThat(captor.getValue().getAction()).isEqualTo("CHECK_IN_SIMULATED");
        assertThat(captor.getValue().getDetail()).isEqualTo("reservation 55 ON_TIME");
    }
}
