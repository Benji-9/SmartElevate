package com.smartelevate.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConfigServiceTest {

    // Configuración válida mínima: cada test cambia o saca una clave.
    private static Map<String, String> valid() {
        Map<String, String> v = new HashMap<>();
        v.put("operating_hours_start", "07:00");
        v.put("operating_hours_end", "22:30");
        v.put("slot_duration", "PT2M");
        v.put("turn_capacity", "10");
        v.put("booking_opens_before", "PT30M");
        v.put("booking_closes_before", "PT2M");
        v.put("max_active_bookings_per_user", "1");
        v.put("max_daily_bookings_per_user", "8");
        v.put("cancellation_deadline_before", "PT1M");
        v.put("cancellation_wait_tolerance", null);
        v.put("check_in_opens_before", "PT1M");
        v.put("check_in_closes_after", "PT2M");
        v.put("no_show_after", "PT60S");
        v.put("no_show_suspension_threshold", "3");
        v.put("no_show_suspension_window", "P7D");
        v.put("no_show_suspension_duration", "PT24H");
        v.put("priority_seats_per_departure", "2");
        v.put("priority_seats_release_before", "PT1M");
        v.put("priority_aging", null);
        v.put("qr_rotation", "PT30S");
        v.put("boarding_time", "PT14S");
        v.put("time_per_floor", "PT3S");
        v.put("low_floors_threshold", null);
        return v;
    }

    private static ConfigService load(Map<String, String> values) {
        ConfigEntryRepository repository = mock(ConfigEntryRepository.class);
        when(repository.findAll()).thenReturn(values.entrySet().stream()
                .map(e -> new ConfigEntry(e.getKey(), e.getValue()))
                .toList());
        return new ConfigService(repository);
    }

    private static Map<String, String> with(String key, String value) {
        Map<String, String> v = valid();
        v.put(key, value);
        return v;
    }

    @Test
    void validConfigurationLoads() {
        assertThatNoException().isThrownBy(() -> load(valid()));
    }

    @ParameterizedTest
    @CsvSource({
            "turn_capacity, 0",
            "turn_capacity, -1",
            "slot_duration, PT0S",
            "operating_hours_start, 22:30",
            "operating_hours_start, 23:00",
            "booking_closes_before, PT30M",
            "booking_closes_before, PT31M",
            "booking_closes_before, PT-1M",
            "max_active_bookings_per_user, 0",
            "max_daily_bookings_per_user, 0",
            "no_show_after, PT121S",
            "no_show_after, PT0S",
            "check_in_closes_after, PT0S",
            "priority_seats_per_departure, 11",
            "priority_seats_per_departure, -1",
            "no_show_suspension_threshold, 0",
            "qr_rotation, PT0S",
            "time_per_floor, PT0S",
            "cancellation_wait_tolerance, PT0S",
            "priority_aging, PT-1M",
    })
    void outOfRangeValuesPreventStartup(String key, String value) {
        assertThatThrownBy(() -> load(with(key, value)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Configuración inválida")
                .hasMessageContaining(key);
    }

    @Test
    void prioritySeatsCanUseTheWholeCapacity() {
        assertThat(load(with("priority_seats_per_departure", "10")).prioritySeatsPerDeparture()).isEqualTo(10);
    }

    @Test
    void malformedValueNamesTheKey() {
        assertThatThrownBy(() -> load(with("turn_capacity", "diez")))
                .hasMessageContaining("turn_capacity")
                .hasMessageContaining("valor inválido");
        assertThatThrownBy(() -> load(with("slot_duration", "2 min")))
                .hasMessageContaining("slot_duration");
    }

    @Test
    void missingKeyPreventsStartup() {
        Map<String, String> v = valid();
        v.remove("turn_capacity");
        assertThatThrownBy(() -> load(v)).hasMessageContaining("Falta el parámetro 'turn_capacity'");

        Map<String, String> withoutPending = valid();
        withoutPending.remove("priority_aging");
        assertThatThrownBy(() -> load(withoutPending)).hasMessageContaining("Falta el parámetro 'priority_aging'");
    }

    @Test
    void requiredValueSetToNullPreventsStartup() {
        assertThatThrownBy(() -> load(with("turn_capacity", null)))
                .hasMessageContaining("'turn_capacity' todavía no está definido");
    }

    @Test
    void parameterToBeDefinedFailsUntilItHasAValue() {
        assertThatThrownBy(() -> load(valid()).priorityAging())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'priority_aging' todavía no está definido");

        assertThat(load(with("priority_aging", "PT5M")).priorityAging()).isEqualTo(Duration.ofMinutes(5));
        assertThat(load(with("low_floors_threshold", "3")).lowFloorsThreshold()).isEqualTo(3);
    }
}
