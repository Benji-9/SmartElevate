package com.smartelevate.turn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.smartelevate.common.mail.EmailSender;
import com.smartelevate.common.time.CampusTime;
import com.smartelevate.elevator.model.Building;
import com.smartelevate.elevator.model.Core;
import com.smartelevate.elevator.model.Elevator;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Plantillas de #221. El mail de ascensor fuera de servicio todavía no tiene consumidor (#134): se prueba acá.
 */
class TurnMailServiceTest {

    private static final Instant DEPARTURE_AT = Instant.parse("2026-10-13T16:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final EmailSender emailSender = mock(EmailSender.class);
    private TurnMailService service;
    private User user;
    private Reservation reservation;
    private TurnMailService.OutOfServiceNotice notice;

    @BeforeEach
    void setUp() {
        service = new TurnMailService(users, emailSender, new CampusTime(Clock.fixed(DEPARTURE_AT, ZoneOffset.UTC),
                ZoneId.of("America/Argentina/Buenos_Aires")));
        user = new User("Ana Pérez", "ana@uade.edu.ar", "1001", "x", DeclaredUserType.TEACHER, DEPARTURE_AT);
        Building building = mock(Building.class);
        given(building.getName()).willReturn("Independencia");
        Core core = mock(Core.class);
        given(core.getName()).willReturn("Independencia 2");
        given(core.getBuilding()).willReturn(building);
        Elevator elevator = mock(Elevator.class);
        given(elevator.getCode()).willReturn("33");
        given(elevator.getCore()).willReturn(core);
        Departure departure = new Departure(elevator, DEPARTURE_AT, 10);
        UUID userId = UUID.randomUUID();
        reservation = new Reservation(userId, departure, 3, 0, 8, DEPARTURE_AT.minusSeconds(1200), true);
        notice = new TurnMailService.OutOfServiceNotice(userId, DEPARTURE_AT, "33", "Independencia 2",
                "Independencia");
        given(users.findById(userId)).willReturn(Optional.of(user));
    }

    @Test
    void outOfServiceMailSaysItIsNotAFaultAndHasNoPriorityOrHealthData() {
        assertThat(service.sendElevatorOutOfService(notice)).isTrue();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(org.mockito.ArgumentMatchers.eq("ana@uade.edu.ar"),
                org.mockito.ArgumentMatchers.eq("Cancelamos tu turno: ascensor fuera de servicio"), body.capture());
        assertThat(body.getValue()).contains("Ana Pérez", "13:00", "ascensor 33", "Independencia 2", "No cuenta como falta")
                .doesNotContainIgnoringCase("prioridad").doesNotContainIgnoringCase("movilidad")
                .doesNotContainIgnoringCase("certificado").doesNotContainIgnoringCase("docente");
    }

    @Test
    void outOfServiceMailRespectsThePreference() {
        user.setNotificationPreferences(true, false);

        assertThat(service.sendElevatorOutOfService(notice)).isFalse();
        verifyNoInteractions(emailSender);
    }

    @Test
    void eachPreferenceOnlyControlsItsOwnMail() {
        user.setNotificationPreferences(false, true);

        assertThat(service.sendDepartureReminder(reservation)).isFalse();
        assertThat(service.sendElevatorOutOfService(notice)).isTrue();
        verify(emailSender).send(any(), any(), any());
    }
}
