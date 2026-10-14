package com.smartelevate.turn.service;

import com.smartelevate.common.config.ConfigService;
import com.smartelevate.common.time.CampusTime;
import com.smartelevate.priority.model.PriorityCategory;
import com.smartelevate.priority.service.PriorityService;
import com.smartelevate.turn.dto.NoShowStatusResponse;
import com.smartelevate.turn.model.ReservationStatus;
import com.smartelevate.turn.repository.ReservationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * No-shows, faltas y suspensión (turnos.md §4 y §5).
 *
 * <p><b>Job:</b> {@link #markNoShows} pasa a NO_SHOW las reservas ACTIVE cuya salida pasó hace más de
 * {@code no_show_after}, con un update condicional ({@code status = 'ACTIVE'}): no pisa un USED de un check-in que
 * se le adelantó y es idempotente. A exactamente +{@code no_show_after} todavía no marca: el check-in hasta ese
 * instante inclusive es a tiempo (check-in-qr.md).
 *
 * <p><b>Faltas:</b> NO_SHOW y CANCELLED_LATE, fechadas en la hora de salida de su reserva. La suspensión se deriva de
 * las reservas, sin tabla propia: {@code no_show_suspension_threshold} faltas dentro de
 * {@code no_show_suspension_window} (la primera estrictamente después de {@code última − window}) suspenden
 * {@code no_show_suspension_duration} desde la última de ellas.
 *
 * <p><b>Prioritarios:</b> la exención se evalúa <b>al intentar reservar</b> (y al consultar el estado), con la
 * prioridad vigente de {@link PriorityService#currentPriority}, nunca con el JWT. Las faltas se registran igual: si
 * la prioridad vence, las que sigan dentro de la ventana cuentan.
 */
@Service
@RequiredArgsConstructor
public class NoShowService {

    static final Set<ReservationStatus> FAULTS = EnumSet.of(ReservationStatus.NO_SHOW, ReservationStatus.CANCELLED_LATE);

    private final ReservationRepository reservations;
    private final ConfigService config;
    private final CampusTime time;
    private final PriorityService priorities;

    /** Corre cada {@code app.no-show-job.interval} (técnico, application.yml). Devuelve cuántas marcó. */
    @Scheduled(fixedDelayString = "${app.no-show-job.interval}")
    public int markNoShows() {
        return reservations.markNoShows(time.now().minus(config.noShowAfter()));
    }

    @Transactional(readOnly = true)
    public NoShowStatusResponse status(UUID userId) {
        Instant now = time.now();
        boolean exempt = priorities.currentPriority(userId) != PriorityCategory.NONE;
        List<Instant> faults = faults(userId, now);
        Instant windowStart = now.minus(config.noShowSuspensionWindow());
        int recent = (int) faults.stream().filter(f -> f.isAfter(windowStart)).count();
        return new NoShowStatusResponse(recent, config.noShowSuspensionThreshold(),
                config.noShowSuspensionWindow().toDays(), config.noShowSuspensionDuration().toHours(),
                exempt ? null : suspendedUntil(faults, now).orElse(null), exempt);
    }

    /** Fin de la suspensión vigente, sin mirar la exención de prioritarios (eso lo decide quien llama). */
    public Optional<Instant> suspendedUntil(UUID userId, Instant now) {
        return suspendedUntil(faults(userId, now), now);
    }

    // Una suspensión vigente empezó hace menos de duration, y sus faltas, hasta window antes de eso.
    private List<Instant> faults(UUID userId, Instant now) {
        Duration lookBack = config.noShowSuspensionWindow().plus(config.noShowSuspensionDuration());
        return reservations.faultTimes(userId, FAULTS, now.minus(lookBack));
    }

    private Optional<Instant> suspendedUntil(List<Instant> faults, Instant now) {
        int threshold = config.noShowSuspensionThreshold();
        Duration window = config.noShowSuspensionWindow();
        Instant until = null;
        for (int i = threshold - 1; i < faults.size(); i++) {
            Instant last = faults.get(i);
            Instant end = last.plus(config.noShowSuspensionDuration());
            if (faults.get(i - threshold + 1).isAfter(last.minus(window)) && end.isAfter(now)) {
                until = end;
            }
        }
        return Optional.ofNullable(until);
    }
}
