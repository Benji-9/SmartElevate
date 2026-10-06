package com.smartelevate.common.config;

import java.time.Duration;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * Parámetros de negocio de la tabla {@code app_config} (ADR 0010), con acceso tipado.
 * Se leen y validan una sola vez al arrancar: si falta un valor o está fuera de rango, la app no levanta.
 * Un cambio en la tabla se aplica reiniciando la app.
 */
@Service
public class ConfigService {

    private final Map<String, String> values = new HashMap<>();

    public ConfigService(ConfigEntryRepository repository) {
        // Sin Collectors.toMap: no acepta los valores NULL de los parámetros a definir.
        repository.findAll().forEach(entry -> values.put(entry.getKey(), entry.getValue()));
        validate();
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

    public Duration qrRotation() {
        return get("qr_rotation", Duration::parse);
    }

    public Duration boardingTime() {
        return get("boarding_time", Duration::parse);
    }

    public Duration timePerFloor() {
        return get("time_per_floor", Duration::parse);
    }

    /** A confirmar: falla hasta que tenga valor. */
    public int lowFloorsThreshold() {
        return get("low_floors_threshold", Integer::parseInt);
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
        check(checkInClosesAfter().isPositive(), "check_in_closes_after tiene que ser mayor a 0");
        check(noShowAfter().isPositive() && noShowAfter().compareTo(checkInClosesAfter()) <= 0,
                "no_show_after tiene que ser mayor a 0 y no superar check_in_closes_after");
        check(noShowSuspensionThreshold() > 0, "no_show_suspension_threshold tiene que ser mayor a 0");
        check(noShowSuspensionWindow().isPositive(), "no_show_suspension_window tiene que ser mayor a 0");
        check(noShowSuspensionDuration().isPositive(), "no_show_suspension_duration tiene que ser mayor a 0");
        check(prioritySeatsPerDeparture() >= 0 && prioritySeatsPerDeparture() <= turnCapacity(),
                "priority_seats_per_departure tiene que estar entre 0 y turn_capacity");
        check(!prioritySeatsReleaseBefore().isNegative(), "priority_seats_release_before no puede ser negativo");
        check(qrRotation().isPositive(), "qr_rotation tiene que ser mayor a 0");
        check(boardingTime().isPositive(), "boarding_time tiene que ser mayor a 0");
        check(timePerFloor().isPositive(), "time_per_floor tiene que ser mayor a 0");
        // Los parámetros a definir solo tienen que existir; si ya tienen valor, se valida.
        if (isDefined("cancellation_wait_tolerance")) {
            check(cancellationWaitTolerance().isPositive(), "cancellation_wait_tolerance tiene que ser mayor a 0");
        }
        if (isDefined("priority_aging")) {
            check(priorityAging().isPositive(), "priority_aging tiene que ser mayor a 0");
        }
        if (isDefined("low_floors_threshold")) {
            lowFloorsThreshold();
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
