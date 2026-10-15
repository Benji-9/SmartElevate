package com.smartelevate.turn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.smartelevate.common.mail.EmailSender;
import com.smartelevate.elevator.model.Kiosk;
import com.smartelevate.elevator.repository.ElevatorRepository;
import com.smartelevate.elevator.repository.KioskRepository;
import com.smartelevate.turn.model.CheckIn;
import com.smartelevate.turn.model.CheckInOutcome;
import com.smartelevate.turn.model.CheckInSource;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.turn.repository.CheckInRepository;
import com.smartelevate.turn.repository.DepartureRepository;
import com.smartelevate.turn.repository.ReservationRepository;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Job de recordatorios (#221) contra H2 con las migraciones y un reloj fijo, con el {@link EmailSender} mockeado.
 * {@code departure_reminder_before} del seed: 5 min.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Import(ReminderServiceTest.FixedClockConfig.class)
class ReminderServiceTest {

    // Martes 2026-10-13 12:55 en Buenos Aires.
    private static final Instant NOW = Instant.parse("2026-10-13T15:55:00Z");
    private static final Duration BEFORE = Duration.ofMinutes(5);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @MockitoBean
    private EmailSender emailSender;
    @Autowired
    private ReminderService job;
    @Autowired
    private UserRepository users;
    @Autowired
    private ElevatorRepository elevators;
    @Autowired
    private DepartureRepository departures;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private CheckInRepository checkIns;
    @Autowired
    private KioskRepository kiosks;

    private User user;
    private int next;

    @AfterEach
    void clean() {
        checkIns.deleteAll();
        kiosks.deleteAll();
        reservations.deleteAll();
        departures.deleteAll();
        users.deleteAll();
    }

    @BeforeEach
    void seed() {
        clean();
        user = newUser();
    }

    private User newUser() {
        next++;
        User u = new User("Ana " + next, "ana" + next + "@uade.edu.ar", "100" + next, "x", DeclaredUserType.STUDENT,
                NOW);
        u.verifyEmail();
        return users.saveAndFlush(u);
    }

    /** Reserva ACTIVE hecha 20 min antes de la salida, que sale {@code fromNow} después de NOW. */
    private Reservation reservation(User owner, Duration fromNow) {
        return reservation(owner, fromNow, NOW.plus(fromNow).minus(Duration.ofMinutes(20)));
    }

    private Reservation reservation(User owner, Duration fromNow, Instant createdAt) {
        Departure d = departures.save(new Departure(elevators.findById("33").orElseThrow(), NOW.plus(fromNow), 10));
        return reservations.save(new Reservation(owner.getId(), d, 1, 0, 8, createdAt));
    }

    @Test
    void sendsExactlyAtTheEdgeOfTheWindowAndNotOneSecondBefore() {
        reservation(user, BEFORE);
        reservation(newUser(), BEFORE.plusSeconds(1));

        assertThat(job.sendDueReminders()).isEqualTo(1);

        verify(emailSender).send(eq("ana1@uade.edu.ar"), anyString(), anyString());
        verify(emailSender, never()).send(eq("ana2@uade.edu.ar"), anyString(), anyString());
    }

    @Test
    void doesNotSendOnceDeparted() {
        reservation(user, Duration.ZERO);
        reservation(newUser(), Duration.ofSeconds(-30));

        assertThat(job.sendDueReminders()).isZero();
        verifyNoInteractions(emailSender);
    }

    @Test
    void sendsOnlyOncePerReservation() {
        reservation(user, Duration.ofMinutes(4));

        assertThat(job.sendDueReminders()).isEqualTo(1);
        assertThat(job.sendDueReminders()).isZero();
        verify(emailSender).send(anyString(), anyString(), anyString());
    }

    @Test
    void theMailHasTheHourAndTheElevatorAndNoPriorityOrHealthData() {
        reservation(user, Duration.ofMinutes(4));

        job.sendDueReminders();

        // 15:59 UTC = 12:59 en Buenos Aires.
        verify(emailSender).send(eq("ana1@uade.edu.ar"), eq("Tu ascensor sale pronto"), contains("12:59"));
        verify(emailSender).send(anyString(), anyString(), contains("ascensor 33"));
        verify(emailSender, never()).send(anyString(), anyString(), org.mockito.ArgumentMatchers.matches("(?is).*(prioridad|movilidad|certificado|docente).*"));
    }

    @Test
    void respectsTheDisabledPreference() {
        user.setNotificationPreferences(false, true);
        users.saveAndFlush(user);
        reservation(user, Duration.ofMinutes(4));

        assertThat(job.sendDueReminders()).isZero();
        verifyNoInteractions(emailSender);
    }

    @Test
    void cancelledUsedAndNoShowReservationsDoNotGetOne() {
        Reservation cancelled = reservation(user, Duration.ofMinutes(4));
        cancelled.cancel(NOW, Duration.ofMinutes(10));
        reservations.save(cancelled);
        Reservation used = reservation(newUser(), Duration.ofMinutes(2));
        used.markUsed();
        reservations.save(used);
        Reservation noShow = reservation(newUser(), Duration.ofMinutes(1));
        noShow.markNoShow();
        reservations.save(noShow);

        assertThat(job.sendDueReminders()).isZero();
        verifyNoInteractions(emailSender);
    }

    @Test
    void aReservationMadeInsideTheWindowGetsNoReminder() {
        reservation(user, Duration.ofMinutes(4), NOW.minusSeconds(30));

        assertThat(job.sendDueReminders()).isZero();
        verifyNoInteractions(emailSender);
    }

    @Test
    void aSimulatedCheckInStillGetsTheReminder() {
        Reservation r = reservation(user, Duration.ofMinutes(4));
        var kiosk = kiosks.save(new Kiosk("33", "abc123", true, NOW));
        checkIns.save(new CheckIn(r.getId(), CheckInOutcome.ON_TIME, "33", CheckInSource.SIMULATED,
                kiosk.getId(), NOW.minusSeconds(60)));

        assertThat(job.sendDueReminders()).isEqualTo(1);
    }

    @Test
    void aFailingMailDoesNotStopTheOthersNorRetries() {
        reservation(user, Duration.ofMinutes(4));
        reservation(newUser(), Duration.ofMinutes(3));
        doThrow(new IllegalStateException("smtp caído")).when(emailSender).send(eq("ana1@uade.edu.ar"), any(), any());

        assertThat(job.sendDueReminders()).isEqualTo(1);
        // Se reclamó antes de mandar: a lo sumo un envío por reserva, el fallo no se reintenta.
        assertThat(job.sendDueReminders()).isZero();
    }
}
