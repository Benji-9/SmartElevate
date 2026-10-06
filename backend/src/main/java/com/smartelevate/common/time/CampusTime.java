package com.smartelevate.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Hora del campus (ADR 0010): los instantes se guardan en UTC y las franjas se calculan en la zona del
 * campus, nunca en la del servidor. Los services piden la hora acá (o al {@link Clock} inyectado), nunca a
 * {@code Instant.now()}, para que los tests y QA puedan fijarla con {@code Clock.fixed(...)}.
 */
@Component
public class CampusTime {

    private final Clock clock;
    private final ZoneId zone;

    public CampusTime(Clock clock, @Value("${app.time.zone}") ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    public ZoneId zone() {
        return zone;
    }

    public Instant now() {
        return clock.instant();
    }

    /** Fecha actual en el campus: a las 22:00 de Buenos Aires ya es mañana en UTC, pero sigue siendo hoy. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    public ZonedDateTime toCampus(Instant instant) {
        return instant.atZone(zone);
    }

    /** Instante UTC de una hora del campus, p. ej. la salida de las 12:56 de hoy. */
    public Instant toInstant(LocalDate date, LocalTime time) {
        return ZonedDateTime.of(date, time, zone).toInstant();
    }
}
