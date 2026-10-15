package com.smartelevate.turn.service;

import com.smartelevate.common.mail.EmailSender;
import com.smartelevate.common.time.CampusTime;
import com.smartelevate.turn.model.Departure;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
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

    /** Datos planos de una reserva cancelada por ascensor fuera de servicio (sin entidades JPA). */
    public record OutOfServiceNotice(UUID userId, Instant departureAt, String elevatorCode, String coreName,
                                     String buildingName) {
    }

    /** Recordatorio de que la salida se acerca. Lo manda el job de {@link ReminderService}. */
    public boolean sendDepartureReminder(Reservation reservation) {
        Departure d = reservation.getDeparture();
        var elevator = d.getElevator();
        return send(reservation.getUserId(), User::isNotifyDepartureReminder, "Tu ascensor sale pronto",
                "Tu turno sale a las %s (%s).\n\nCuando llegues, escaneá el QR del ascensor para hacer el check-in. "
                        + "Si ya no podés viajar, cancelá desde la app.",
                d.getDepartureAt(), elevator.getCode(), elevator.getCore().getName(),
                elevator.getCore().getBuilding().getName());
    }

    /**
     * Cancelación automática por ascensor fuera de servicio, sin falta (turnos.md §8, #134). Todavía no tiene
     * consumidor. #134 tiene que llamarlo <b>después del commit</b> de la cancelación (p. ej. un
     * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}), nunca dentro de la transacción: si el SMTP es lento
     * retiene la conexión, y si la transacción hace rollback el mail ya salió. Recibe datos planos, así que el
     * llamador los arma antes del commit y no depende de entidades ni de lazy loading.
     */
    public boolean sendElevatorOutOfService(OutOfServiceNotice n) {
        return send(n.userId(), User::isNotifyElevatorOutOfService, "Cancelamos tu turno: ascensor fuera de servicio",
                "Tu turno de las %s (%s) se canceló porque el ascensor salió de servicio. "
                        + "No cuenta como falta y se te devolvió el cupo del día.\n\n"
                        + "Podés reservar otra salida desde la app.",
                n.departureAt(), n.elevatorCode(), n.coreName(), n.buildingName());
    }

    private boolean send(UUID userId, Predicate<User> wants, String subject, String bodyTemplate, Instant departureAt,
                         String elevatorCode, String coreName, String buildingName) {
        Optional<User> user = users.findById(userId).filter(wants);
        if (user.isEmpty()) {
            return false;
        }
        String where = "ascensor " + elevatorCode + ", " + coreName + ", " + buildingName;
        String hour = time.toCampus(departureAt).format(HOUR);
        emailSender.send(user.get().getEmail(), subject,
                "Hola " + user.get().getFullName() + ",\n\n" + bodyTemplate.formatted(hour, where));
        return true;
    }
}
