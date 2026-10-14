package com.smartelevate.turn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import com.smartelevate.common.config.ConfigService;
import com.smartelevate.common.time.CampusTime;
import com.smartelevate.priority.model.PriorityCategory;
import com.smartelevate.priority.service.PriorityService;
import com.smartelevate.turn.dto.NoShowStatusResponse;
import com.smartelevate.turn.repository.ReservationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Regla de turnos.md §5: 3 faltas en 7 días → 24 h sin reservar; prioritarios exentos. */
@ExtendWith(MockitoExtension.class)
class NoShowServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:55:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final Duration DAY = Duration.ofDays(1);

    @Mock
    private ReservationRepository reservations;
    @Mock
    private ConfigService config;
    @Mock
    private PriorityService priorities;

    private NoShowService service;

    @BeforeEach
    void setUp() {
        CampusTime time = new CampusTime(Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("America/Argentina/Buenos_Aires"));
        service = new NoShowService(reservations, config, time, priorities);
        lenient().when(config.noShowAfter()).thenReturn(Duration.ofSeconds(60));
        lenient().when(config.noShowSuspensionThreshold()).thenReturn(3);
        lenient().when(config.noShowSuspensionWindow()).thenReturn(Duration.ofDays(7));
        lenient().when(config.noShowSuspensionDuration()).thenReturn(Duration.ofHours(24));
        lenient().when(priorities.currentPriority(USER)).thenReturn(PriorityCategory.NONE);
    }

    /** Faltas a estos tiempos antes de NOW (la lista llega ordenada, la más vieja primero). */
    private void faultsAgo(Duration... ago) {
        given(reservations.faultTimes(USER, NoShowService.FAULTS, NOW.minus(Duration.ofDays(8))))
                .willReturn(Arrays.stream(ago).map(NOW::minus).sorted().toList());
    }

    @Test
    void theJobMarksWhatDepartedMoreThanNoShowAfterAgo() {
        given(reservations.markNoShows(any())).willReturn(2);

        assertThat(service.markNoShows()).isEqualTo(2);
        verify(reservations).markNoShows(NOW.minusSeconds(60));
    }

    @Test
    void twoFaultsInSevenDaysDoNotSuspend() {
        faultsAgo(DAY, Duration.ofHours(1));

        assertThat(service.suspendedUntil(USER, NOW)).isEmpty();
        NoShowStatusResponse status = service.status(USER);
        assertThat(status.recentNoShows()).isEqualTo(2);
        assertThat(status.suspendedUntil()).isNull();
    }

    @Test
    void theThirdFaultInSevenDaysSuspendsForTwentyFourHoursFromIt() {
        faultsAgo(Duration.ofDays(6), DAY, Duration.ofHours(1));

        assertThat(service.suspendedUntil(USER, NOW)).contains(NOW.minus(Duration.ofHours(1)).plus(DAY));
    }

    @Test
    void aFaultSevenDaysBeforeTheThirdIsOutsideTheWindow() {
        // La 1.ª queda exactamente 7 días antes de la 3.ª: la ventana es (3.ª − 7 días, 3.ª].
        faultsAgo(Duration.ofDays(7).plusHours(1), DAY, Duration.ofHours(1));

        assertThat(service.suspendedUntil(USER, NOW)).isEmpty();
        assertThat(service.status(USER).recentNoShows()).isEqualTo(2);
    }

    @Test
    void theSuspensionEndsTwentyFourHoursAfterTheThirdFault() {
        faultsAgo(Duration.ofDays(3), Duration.ofDays(2), DAY);
        assertThat(service.suspendedUntil(USER, NOW)).isEmpty(); // termina justo ahora

        faultsAgo(Duration.ofDays(3), Duration.ofDays(2), DAY.minusSeconds(1));
        assertThat(service.suspendedUntil(USER, NOW)).contains(NOW.plusSeconds(1));
    }

    @Test
    void aPriorityUserIsExemptButTheFaultsAreStillCounted() {
        faultsAgo(Duration.ofDays(2), DAY, Duration.ofHours(1));
        given(priorities.currentPriority(USER)).willReturn(PriorityCategory.TEACHER);

        NoShowStatusResponse status = service.status(USER);

        assertThat(status.exempt()).isTrue();
        assertThat(status.suspendedUntil()).isNull();
        assertThat(status.recentNoShows()).isEqualTo(3);
    }

    @Test
    void theStatusCarriesTheRuleParameters() {
        faultsAgo(Duration.ofDays(2), DAY, Duration.ofHours(1));

        assertThat(service.status(USER)).isEqualTo(new NoShowStatusResponse(3, 3, 7, 24,
                NOW.minus(Duration.ofHours(1)).plus(DAY), false));
    }
}
