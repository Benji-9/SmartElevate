package com.smartelevate.turn.service;

import com.smartelevate.common.config.ConfigService;
import com.smartelevate.common.time.CampusTime;
import com.smartelevate.priority.model.PriorityCategory;
import com.smartelevate.priority.service.PriorityService;
import com.smartelevate.turn.dto.NoShowStatusResponse;
import com.smartelevate.turn.model.NoShowSuspension;
import com.smartelevate.turn.model.ReservationStatus;
import com.smartelevate.turn.repository.NoShowSuspensionRepository;
import com.smartelevate.turn.repository.ReservationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
 * <p><b>Faltas:</b> NO_SHOW y CANCELLED_LATE, fechadas en la hora de salida de su reserva. Una reserva con check-in
 * simulado no es falta ({@link ReservationRepository#NOT_SIMULATED}, el mismo criterio que {@link KpiEvents}).
 *
 * <p><b>Suspensión (V17):</b> la falta que completa {@code no_show_suspension_threshold} dentro de
 * {@code no_show_suspension_window} (la primera estrictamente después de {@code esa falta − window}) dispara
 * {@code no_show_suspension_duration} desde ella. Después, el conteo <b>arranca de cero desde el fin</b> de la
 * suspensión: las faltas durante la suspensión no cuentan para la siguiente. Las suspensiones se derivan de las
 * faltas en orden, así que da igual cuándo se evalúen; {@link #suspendedUntil} guarda las que falten (bajo el lock
 * del usuario que toma reservar, y con el único de V17 como red).
 *
 * <p><b>Prioritarios:</b> la exención se evalúa <b>al intentar reservar</b> (y al consultar el estado), con la
 * prioridad vigente de {@link PriorityService#currentPriority}, nunca con el JWT: no se los bloquea ni se les guarda
 * suspensión. Las faltas se registran igual: si la prioridad vence, cuentan las que haya desde la última suspensión.
 */
@Service
@RequiredArgsConstructor
public class NoShowService {

    static final Set<ReservationStatus> FAULTS = EnumSet.of(ReservationStatus.NO_SHOW, ReservationStatus.CANCELLED_LATE);

    private final ReservationRepository reservations;
    private final NoShowSuspensionRepository suspensions;
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
        Evaluation e = evaluate(userId);
        Optional<Instant> until = exempt ? Optional.empty() : e.until(now);
        Instant windowStart = now.minus(config.noShowSuspensionWindow());
        // Sin suspensión vigente: las que cuentan para la próxima. Suspendido o exento: todas las de la ventana.
        List<Instant> shown = exempt || until.isPresent()
                ? reservations.faultTimes(userId, FAULTS, windowStart) : e.counting();
        int recent = (int) shown.stream().filter(f -> f.isAfter(windowStart)).count();
        return new NoShowStatusResponse(recent, config.noShowSuspensionThreshold(),
                config.noShowSuspensionWindow().toDays(), config.noShowSuspensionDuration().toHours(),
                until.orElse(null), exempt);
    }

    /**
     * Fin de la suspensión vigente. Guarda las suspensiones nuevas que deriven las faltas. No mira la exención de
     * prioritarios: quien llama no lo invoca para ellos. Llamar con la fila del usuario bloqueada.
     */
    @Transactional
    public Optional<Instant> suspendedUntil(UUID userId, Instant now) {
        Evaluation e = evaluate(userId);
        suspensions.saveAll(e.created());
        return e.until(now);
    }

    /**
     * @param created suspensiones derivadas que todavía no están guardadas
     * @param lastEnd fin de la última suspensión (guardada o derivada), o null
     * @param counting faltas desde {@code lastEnd} que siguen dentro de la ventana de la última: las que cuentan
     */
    private record Evaluation(List<NoShowSuspension> created, Instant lastEnd, List<Instant> counting) {
        Optional<Instant> until(Instant now) {
            return lastEnd != null && lastEnd.isAfter(now) ? Optional.of(lastEnd) : Optional.empty();
        }
    }

    private Evaluation evaluate(UUID userId) {
        int threshold = config.noShowSuspensionThreshold();
        Duration window = config.noShowSuspensionWindow();
        Instant lastEnd = suspensions.findFirstByUserIdOrderByEndsAtDesc(userId)
                .map(NoShowSuspension::getEndsAt).orElse(null);
        // ponytail: sin suspensiones previas lee todas las faltas del usuario; alcanza para el piloto. Si crece,
        // guardar la suspensión en el momento de la falta (job, cancelación, check-in) y acotar la lectura.
        List<Instant> faults = reservations.faultTimes(userId, FAULTS, lastEnd == null ? Instant.EPOCH : lastEnd);
        List<NoShowSuspension> created = new ArrayList<>();
        Deque<Instant> counting = new ArrayDeque<>();
        for (Instant fault : faults) {
            if (lastEnd != null && fault.isBefore(lastEnd)) {
                continue; // durante la suspensión: no cuenta para la siguiente
            }
            counting.addLast(fault);
            while (!counting.getFirst().isAfter(fault.minus(window))) {
                counting.removeFirst();
            }
            if (counting.size() >= threshold) {
                lastEnd = fault.plus(config.noShowSuspensionDuration());
                created.add(new NoShowSuspension(userId, fault, lastEnd));
                counting.clear();
            }
        }
        return new Evaluation(created, lastEnd, List.copyOf(counting));
    }
}
