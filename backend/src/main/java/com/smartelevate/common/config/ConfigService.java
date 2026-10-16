package com.smartelevate.common.config;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import com.smartelevate.common.time.CampusTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Parámetros de negocio de la tabla {@code app_config} (ADR 0010), con acceso tipado.
 * Se leen y validan una sola vez al arrancar: si falta un valor o está fuera de rango, la app no levanta.
 * Un cambio en la tabla se aplica reiniciando la app.
 */
@Slf4j
@Service
public class ConfigService {

    private final Map<String, String> values = new HashMap<>();

    public ConfigService(ConfigEntryRepository repository, CampusTime time) {
        // Sin Collectors.toMap: no acepta los valores NULL de los parámetros a definir.
        repository.findAll().forEach(entry -> values.put(entry.getKey(), entry.getValue()));
        validate();
        warnIfNoSemesterEndAhead(time.today());
    }

    // No frena el arranque: solo la aprobación de docentes depende de esto, y falla con su propio mensaje.
    private void warnIfNoSemesterEndAhead(LocalDate today) {
        if (semesterEndDates().stream().allMatch(end -> end.isBefore(today))) {
            log.warn("semester_end_dates no tiene ningún cierre de cuatrimestre desde hoy ({}): no se van a poder "
                    + "aprobar docentes hasta cargar el próximo en app_config y reiniciar la app", today);
        }
    }

    public LocalTime operatingHoursStart() {
        return get("operating_hours_start", LocalTime::parse);
    }

    public LocalTime operatingHoursEnd() {
        return get("operating_hours_end", LocalTime::parse);
    }

    public Duration slotDuration() {
        return get("slot_duration", Duration::parse);
    }

    public int turnCapacity() {
        return get("turn_capacity", Integer::parseInt);
    }

    public Duration bookingOpensBefore() {
        return get("booking_opens_before", Duration::parse);
    }

    public Duration bookingClosesBefore() {
        return get("booking_closes_before", Duration::parse);
    }

    public int maxActiveBookingsPerUser() {
        return get("max_active_bookings_per_user", Integer::parseInt);
    }

    public int maxDailyBookingsPerUser() {
        return get("max_daily_bookings_per_user", Integer::parseInt);
    }

    public Duration cancellationDeadlineBefore() {
        return get("cancellation_deadline_before", Duration::parse);
    }

    /** A definir: falla hasta que tenga valor. */
    public Duration cancellationWaitTolerance() {
        return get("cancellation_wait_tolerance", Duration::parse);
    }

    public Duration checkInOpensBefore() {
        return get("check_in_opens_before", Duration::parse);
    }

    public Duration checkInClosesAfter() {
        return get("check_in_closes_after", Duration::parse);
    }

    /** Cuánto antes de la salida se manda el recordatorio por email (#221). */
    public Duration departureReminderBefore() {
        return get("departure_reminder_before", Duration::parse);
    }

    public Duration noShowAfter() {
        return get("no_show_after", Duration::parse);
    }

    public int noShowSuspensionThreshold() {
        return get("no_show_suspension_threshold", Integer::parseInt);
    }

    public Duration noShowSuspensionWindow() {
        return get("no_show_suspension_window", Duration::parse);
    }

    public Duration noShowSuspensionDuration() {
        return get("no_show_suspension_duration", Duration::parse);
    }

    public int prioritySeatsPerDeparture() {
        return get("priority_seats_per_departure", Integer::parseInt);
    }

    public Duration prioritySeatsReleaseBefore() {
        return get("priority_seats_release_before", Duration::parse);
    }

    /** A definir: falla hasta que tenga valor. */
    public Duration priorityAging() {
        return get("priority_aging", Duration::parse);
    }

    /** Cierres de cuatrimestre, fechas del campus en orden: la prioridad de docente vence al fin del vigente. */
    public List<LocalDate> semesterEndDates() {
        return get("semester_end_dates", raw -> Arrays.stream(raw.split(",")).map(String::trim)
                .map(LocalDate::parse).toList());
    }

    public Duration boardingTime() {
        return get("boarding_time", Duration::parse);
    }

    public Duration timePerFloor() {
        return get("time_per_floor", Duration::parse);
    }

    /** Pisos bajos: no se reserva con destino entre el 1 y este piso (0 = sin restricción). */
    public int lowFloorsThreshold() {
        return get("low_floors_threshold", Integer::parseInt);
    }

    /** Ocupación (%) desde la que un núcleo tiene congestión media. A calibrar en el piloto. */
    public int congestionMediumFrom() {
        return get("congestion_medium_from", Integer::parseInt);
    }

    /** Ocupación (%) desde la que un núcleo tiene congestión alta. A calibrar en el piloto. */
    public int congestionHighFrom() {
        return get("congestion_high_from", Integer::parseInt);
    }

    /** Turno mañana de cursada (kpis.md#filtros), hora de Buenos Aires. */
    public LocalTime morningShiftStart() {
        return get("morning_shift_start", LocalTime::parse);
    }

    /** Fin exclusivo del turno mañana de cursada. */
    public LocalTime morningShiftEnd() {
        return get("morning_shift_end", LocalTime::parse);
    }

    // Cada accessor parsea su valor, así que llamarlos acá también detecta claves faltantes y formatos inválidos.
    private void validate() {
        check(operatingHoursStart().isBefore(operatingHoursEnd()),
                "operating_hours_start tiene que ser anterior a operating_hours_end");
        check(slotDuration().isPositive(), "slot_duration tiene que ser mayor a 0");
        check(turnCapacity() > 0, "turn_capacity tiene que ser mayor a 0");
        check(bookingClosesBefore().compareTo(bookingOpensBefore()) < 0,
                "booking_closes_before tiene que ser menor que booking_opens_before");
        check(!bookingClosesBefore().isNegative(), "booking_closes_before no puede ser negativo");
        check(maxActiveBookingsPerUser() > 0, "max_active_bookings_per_user tiene que ser mayor a 0");
        check(maxDailyBookingsPerUser() >= maxActiveBookingsPerUser(),
                "max_daily_bookings_per_user no puede ser menor que max_active_bookings_per_user");
        check(!cancellationDeadlineBefore().isNegative(), "cancellation_deadline_before no puede ser negativo");
        check(!checkInOpensBefore().isNegative(), "check_in_opens_before no puede ser negativo");
        // El check-in conserva el lugar (V16): si abriera antes del cierre de la reserva, otro podría pedirlo.
        check(checkInOpensBefore().compareTo(bookingClosesBefore()) <= 0,
                "check_in_opens_before no puede superar booking_closes_before");
        check(checkInClosesAfter().isPositive(), "check_in_closes_after tiene que ser mayor a 0");
        check(noShowAfter().isPositive() && noShowAfter().compareTo(checkInClosesAfter()) <= 0,
                "no_show_after tiene que ser mayor a 0 y no superar check_in_closes_after");
        check(departureReminderBefore().isPositive() && departureReminderBefore().compareTo(bookingOpensBefore()) < 0,
                "departure_reminder_before tiene que ser mayor a 0 y menor que booking_opens_before");
        check(noShowSuspensionThreshold() > 0, "no_show_suspension_threshold tiene que ser mayor a 0");
        check(noShowSuspensionWindow().isPositive(), "no_show_suspension_window tiene que ser mayor a 0");
        check(noShowSuspensionDuration().isPositive(), "no_show_suspension_duration tiene que ser mayor a 0");
        check(prioritySeatsPerDeparture() >= 0 && prioritySeatsPerDeparture() <= turnCapacity(),
                "priority_seats_per_departure tiene que estar entre 0 y turn_capacity");
        // Liberados después del cierre de la ventana, nadie podría reservarlos (#113).
        check(prioritySeatsReleaseBefore().compareTo(bookingClosesBefore()) > 0,
                "priority_seats_release_before tiene que ser mayor que booking_closes_before");
        check(boardingTime().isPositive(), "boarding_time tiene que ser mayor a 0");
        check(timePerFloor().isPositive(), "time_per_floor tiene que ser mayor a 0");
        check(lowFloorsThreshold() >= 0, "low_floors_threshold no puede ser negativo");
        check(congestionMediumFrom() > 0 && congestionMediumFrom() < congestionHighFrom()
                        && congestionHighFrom() <= 100,
                "congestion_medium_from y congestion_high_from tienen que cumplir 0 < media < alta <= 100");
        check(morningShiftStart().isBefore(morningShiftEnd()),
                "morning_shift_start tiene que ser anterior a morning_shift_end");
        List<LocalDate> semesterEnds = semesterEndDates();
        check(semesterEnds.equals(semesterEnds.stream().distinct().sorted().toList()),
                "semester_end_dates tiene que estar en orden y sin fechas repetidas");
        // Los parámetros a definir solo tienen que existir; si ya tienen valor, se valida.
        if (isDefined("cancellation_wait_tolerance")) {
            check(cancellationWaitTolerance().isPositive(), "cancellation_wait_tolerance tiene que ser mayor a 0");
        }
        if (isDefined("priority_aging")) {
            check(priorityAging().isPositive(), "priority_aging tiene que ser mayor a 0");
        }
    }

    private boolean isDefined(String key) {
        if (!values.containsKey(key)) {
            throw new IllegalStateException("Falta el parámetro '%s' en app_config".formatted(key));
        }
        return values.get(key) != null;
    }

    private <T> T get(String key, Function<String, T> parser) {
        if (!isDefined(key)) {
            throw new IllegalStateException(
                    "El parámetro '%s' todavía no está definido (es NULL en app_config)".formatted(key));
        }
        String raw = values.get(key);
        try {
            return parser.apply(raw.trim());
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "El parámetro '%s' tiene un valor inválido: '%s'".formatted(key, raw), ex);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Configuración inválida: " + message);
        }
    }
}
