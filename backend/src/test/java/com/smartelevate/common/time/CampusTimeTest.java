package com.smartelevate.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CampusTimeTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    private static CampusTime at(String utcInstant) {
        return new CampusTime(Clock.fixed(Instant.parse(utcInstant), ZoneOffset.UTC), BUENOS_AIRES);
    }

    @Test
    void nowComesFromTheInjectedClock() {
        assertThat(at("2026-10-06T15:55:00Z").now()).isEqualTo(Instant.parse("2026-10-06T15:55:00Z"));
    }

    @Test
    void todayIsTheCampusDateEvenWhenUtcAlreadyChangedDay() {
        // 22:30 del martes en Buenos Aires = 01:30 del miércoles en UTC.
        CampusTime time = at("2026-10-07T01:30:00Z");

        assertThat(time.today()).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(time.toCampus(time.now()).toLocalTime()).isEqualTo(LocalTime.of(22, 30));
    }

    @Test
    void campusTimeConvertsToUtcInstant() {
        CampusTime time = at("2026-10-06T12:00:00Z");

        assertThat(time.toInstant(LocalDate.of(2026, 10, 6), LocalTime.of(12, 56)))
                .isEqualTo(Instant.parse("2026-10-06T15:56:00Z"));
        assertThat(time.toInstant(LocalDate.of(2026, 10, 6), LocalTime.of(21, 0)))
                .isEqualTo(Instant.parse("2026-10-07T00:00:00Z"));
    }
}
