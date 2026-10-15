package com.smartelevate.turn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "KPIs de congestión del período (docs/reglas/kpis.md), sin las reservas con check-in simulado. "
        + "Check-ins y ocupación cuentan solo las salidas ya resueltas (pasado el umbral de no-show)")
public record AdminKpisResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Inicio del período, en UTC",
                example = "2026-10-13T03:00:00Z") Instant from,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Fin exclusivo del período, en UTC",
                example = "2026-10-14T03:00:00Z") Instant to,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "Espera real "
                + "promedio en segundos. Null: no se mide todavía (necesita la encuesta, fuera del MVP)")
        Double avgWaitSeconds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "% que espera 5–10 min "
                + "según la encuesta. Null: la encuesta está fuera del MVP") Double wait5To10Percent,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Línea base de la encuesta inicial: % "
                + "que esperaba 5–10 min", example = "43.5") double baselinePercent,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Reservas no canceladas",
                example = "1284") long reservations,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Check-ins cumplidos (en su ascensor o "
                + "en otro) de las salidas resueltas", example = "1100") long checkIns,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "Check-ins cumplidos / "
                + "reservas no canceladas de las salidas resueltas, en %. Null sin salidas resueltas",
                example = "86.7") Double checkInPercent,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "Personas con check-in "
                + "cumplido por salida resuelta con reservas. Null sin salidas resueltas", example = "7.6")
        Double avgOccupancy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Lugares por salida", example = "10")
        int capacity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Reservas por hora de salida (Buenos "
                + "Aires), de cada hora del horario de operación o del turno, en orden")
        List<HourReservations> reservationsByHour,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Núcleos con reservas en el período")
        List<CoreKpis> cores,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "Mismos totales del "
                + "período anterior de igual largo y mismos filtros. Null si no hubo reservas")
        PreviousKpis previous) {

    @Schema(name = "HourReservations")
    public record HourReservations(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "8") int hour,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "120") long reservations) {
    }

    @Schema(name = "CoreKpis", description = "Estado de un núcleo en el período")
    public record CoreKpis(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L1") String coreId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Lima 1") String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "212") long reservations,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "Personas con "
                    + "check-in cumplido / capacidad de sus salidas resueltas, en %. Null sin salidas resueltas",
                    example = "58") Double occupancyPercent,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "Null: la espera "
                    + "real no se mide todavía") Double avgWaitSeconds,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "Nivel según la "
                    + "ocupación. Null sin salidas resueltas") CongestionLevel congestion) {
    }

    @Schema(name = "PreviousKpis", description = "Totales del período anterior, para comparar")
    public record PreviousKpis(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-12T03:00:00Z") Instant from,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-13T03:00:00Z") Instant to,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1190") long reservations,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "84.2")
            Double checkInPercent,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "7.1")
            Double avgOccupancy,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Double avgWaitSeconds,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Double wait5To10Percent) {
    }
}
