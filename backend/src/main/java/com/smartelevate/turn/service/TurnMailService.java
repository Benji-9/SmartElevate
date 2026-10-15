package com.smartelevate.turn.service;

import com.smartelevate.common.mail.EmailSender;
import com.smartelevate.common.time.CampusTime;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Mails de turnos (#221). Respetan las preferencias del usuario y no llevan datos de salud ni de prioridad: solo
 * ascensor, edificio y hora. Cada método devuelve si mandó el mail (false si el usuario lo desactivó).
 *
 * <p>Esperan la reserva con su salida, ascensor, núcleo y edificio ya cargados.
 */
@Service
@RequiredArgsConstructor
public class TurnMailService {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final UserRepository users;
    private final EmailSender emailSender;
    private final CampusTime time;

    /** Recordatorio de que la salida se acerca. Lo manda el job de {@link ReminderService}. */
    public boolean sendDepartureReminder(Reservation reservation) {
        return send(reservation, User::isNotifyDepartureReminder, "Tu ascensor sale pronto",
                "Tu turno sale a las %s (%s).\n\nCuando llegues, escaneá el QR del ascensor para hacer el check-in. "
                        + "Si ya no podés viajar, cancelá desde la app.");
    }

    /**
     * Cancelación automática por ascensor fuera de servicio, sin falta (turnos.md §8, #134). Todavía no tiene
     * consumidor: lo va a llamar #134 al marcar el ascensor fuera de servicio, por cada reserva cancelada.
     */
    public boolean sendElevatorOutOfService(Reservation reservation) {
        return send(reservation, User::isNotifyElevatorOutOfService,
                "Cancelamos tu turno: ascensor fuera de servicio",
                "Tu turno de las %s (%s) se canceló porque el ascensor salió de servicio. "
                        + "No cuenta como falta y se te devolvió el cupo del día.\n\n"
                        + "Podés reservar otra salida desde la app.");
    }

    private boolean send(Reservation reservation, Predicate<User> wants, String subject, String bodyTemplate) {
        Optional<User> user = users.findById(reservation.getUserId()).filter(wants);
        if (user.isEmpty()) {
            return false;
        }
        Departure d = reservation.getDeparture();
        var elevator = d.getElevator();
        String where = "ascensor " + elevator.getCode() + ", " + elevator.getCore().getName() + ", "
                + elevator.getCore().getBuilding().getName();
        String hour = time.toCampus(d.getDepartureAt()).format(HOUR);
        emailSender.send(user.get().getEmail(), subject,
                "Hola " + user.get().getFullName() + ",\n\n" + bodyTemplate.formatted(hour, where));
        return true;
    }
}
