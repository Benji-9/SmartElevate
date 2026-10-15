package com.smartelevate.turn.service;

import com.smartelevate.common.config.ConfigService;
import com.smartelevate.common.time.CampusTime;
import com.smartelevate.turn.model.Reservation;
import com.smartelevate.turn.repository.ReservationRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Recordatorio por email de la salida (#221). Corre cada {@code app.reminder-job.interval} (técnico) y se apaga con
 * {@code app.reminder-job.enabled=false}. Manda a las reservas ACTIVE cuya salida cae en
 * {@code (ahora, ahora + departure_reminder_before]} (app_config).
 *
 * <p><b>Un solo envío por reserva:</b> se reclama con un update condicional ({@code reminder_sent_at IS NULL}) antes de
 * mandar. Si el mail falla no se reintenta (a lo sumo uno, no al menos uno). Las reservas hechas dentro de esa ventana
 * no reciben recordatorio: la persona acaba de reservar. Las de check-in simulado lo reciben como cualquier otra.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.reminder-job.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderService {

    private final ReservationRepository reservations;
    private final TurnMailService mails;
    private final ConfigService config;
    private final CampusTime time;

    /** Devuelve cuántos mails mandó. */
    @Scheduled(fixedDelayString = "${app.reminder-job.interval}")
    public int sendDueReminders() {
        Instant now = time.now();
        Duration before = config.departureReminderBefore();
        int sent = 0;
        for (Reservation r : reservations.findDueForReminder(now, now.plus(before))) {
            if (r.getCreatedAt().isAfter(r.getDeparture().getDepartureAt().minus(before))
                    || reservations.claimReminder(r.getId(), now) == 0) {
                continue;
            }
            try {
                if (mails.sendDepartureReminder(r)) {
                    sent++;
                }
            } catch (RuntimeException e) {
                // Sin la excepción entera: MailSendException trae el email del destinatario.
                log.error("No se pudo mandar el recordatorio de la reserva {}: {}", r.getId(), e.getClass().getName());
            }
        }
        return sent;
    }
}
