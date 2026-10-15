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
import com.smartelevate.turn.model.NoShowSuspension;
import com.smartelevate.turn.repository.NoShowSuspensionRepository;
import com.smartelevate.turn.repository.ReservationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regla de turnos.md §5: 3 faltas en 7 días → 24 h sin reservar; el conteo arranca de cero al terminar cada
 * suspensión; prioritarios exentos.
 */
@ExtendWith(MockitoExtension.class)
class NoShowServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:55:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final Duration DAY = Duration.ofDays(1);
    private static final Duration HOUR = Duration.ofHours(1);

    @Mock
    private ReservationRepository reservations;
    @Mock
    private NoShowSuspensionRepository suspensions;
    @Mock
    private ConfigService config;
    @Mock
    private PriorityService priorities;
    @Captor
    private ArgumentCaptor<List<NoShowSuspension>> saved;

    private NoShowService service;

    @BeforeEach
    void setUp() {
        CampusTime time = new CampusTime(Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("America/Argentina/Buenos_Aires"));
        service = new NoShowService(reservations, suspensions, config, time, priorities);
        lenient().when(config.noShowAfter()).thenReturn(Duration.ofSeconds(60));
        lenient().when(config.noShowSuspensionThreshold()).thenReturn(3);
        lenient().when(config.noShowSuspensionWindow()).thenReturn(Duration.ofDays(7));
        lenient().when(config.noShowSuspensionDuration()).thenReturn(Duration.ofHours(24));
        lenient().when(priorities.currentPriority(USER)).thenReturn(PriorityCategory.NONE);
        lenient().when(suspensions.findFirstByUserIdOrderByEndsAtDesc(USER)).thenReturn(Optional.empty());
    }

    /** Faltas a estos tiempos antes de NOW, leídas desde {@code from} (la lista llega ordenada). */
    private void faultsFrom(Instant from, Duration... ago) {
        given(reservations.faultTimes(USER, NoShowService.FAULTS, from))
                .willReturn(Arrays.stream(ago).map(NOW::minus).sorted().toList());
    }

    private void faultsAgo(Duration... ago) {
        faultsFrom(Instant.EPOCH, ago);
    }

    private List<NoShowSuspension> savedSuspensions() {
        verify(suspensions).saveAll(saved.capture());
        return saved.getValue();
    }

    @Test
    void theJobMarksWhatDepartedMoreThanNoShowAfterAgo() {
        given(reservations.markNoShows(any())).willReturn(2);

        assertThat(service.markNoShows()).isEqualTo(2);
        verify(reservations).markNoShows(NOW.minusSeconds(60));
    }

    @Test
    void twoFaultsInSevenDaysDoNotSuspend() {
        faultsAgo(DAY, HOUR);

        assertThat(service.suspendedUntil(USER, NOW)).isEmpty();
        assertThat(savedSuspensions()).isEmpty();
        assertThat(service.status(USER).recentNoShows()).isEqualTo(2);
    }

    @Test
    void theThirdFaultInSevenDaysCreatesATwentyFourHourSuspensionFromIt() {
        faultsAgo(Duration.ofDays(6), DAY, HOUR);
        Instant third = NOW.minus(HOUR);

        assertThat(service.suspendedUntil(USER, NOW)).contains(third.plus(DAY));
        assertThat(savedSuspensions()).singleElement().satisfies(s -> {
            assertThat(s.getUserId()).isEqualTo(USER);
            assertThat(s.getTriggerFaultAt()).isEqualTo(third);
            assertThat(s.getStartsAt()).isEqualTo(third);
            assertThat(s.getEndsAt()).isEqualTo(third.plus(DAY));
        });
    }

    @Test
    void aFaultSevenDaysBeforeTheThirdIsOutsideTheWindow() {
        // La 1.ª queda exactamente 7 días antes de la 3.ª: la ventana es (3.ª − 7 días, 3.ª].
        faultsAgo(Duration.ofDays(7).plus(HOUR), DAY, HOUR);

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
    void faultsDuringASuspensionDoNotCountAndTheCountRestartsAfterIt() {
        // Suspensión de -6 d a -5 d; una falta durante, dos después: no alcanza para otra.
        faultsAgo(Duration.ofDays(6).plus(Duration.ofHours(2)), Duration.ofDays(6).plus(HOUR), Duration.ofDays(6),
                Duration.ofDays(5).plus(HOUR), Duration.ofDays(2), DAY);

        assertThat(service.suspendedUntil(USER, NOW)).isEmpty();
        assertThat(savedSuspensions()).singleElement()
                .satisfies(s -> assertThat(s.getEndsAt()).isEqualTo(NOW.minus(Duration.ofDays(5))));
        assertThat(service.status(USER).recentNoShows()).isEqualTo(2);
    }

    @Test
    void threeNewFaultsAfterTheSuspensionSuspendAgain() {
        faultsAgo(Duration.ofDays(6).plus(Duration.ofHours(2)), Duration.ofDays(6).plus(HOUR), Duration.ofDays(6),
                Duration.ofDays(3), Duration.ofDays(2), HOUR);

        assertThat(service.suspendedUntil(USER, NOW)).contains(NOW.minus(HOUR).plus(DAY));
        assertThat(savedSuspensions()).extracting(NoShowSuspension::getTriggerFaultAt)
                .containsExactly(NOW.minus(Duration.ofDays(6)), NOW.minus(HOUR));
    }

    @Test
    void aSavedSuspensionIsTheStartingPointAndIsNotCreatedTwice() {
        Instant end = NOW.minus(Duration.ofDays(2));
        NoShowSuspension last = new NoShowSuspension(USER, end.minus(DAY), end);
        given(suspensions.findFirstByUserIdOrderByEndsAtDesc(USER)).willReturn(Optional.of(last));
        faultsFrom(end, DAY, HOUR);

        assertThat(service.suspendedUntil(USER, NOW)).isEmpty();
        assertThat(savedSuspensions()).isEmpty();
    }

    @Test
    void aSavedSuspensionStillRunningBlocks() {
        Instant end = NOW.plus(HOUR);
        given(suspensions.findFirstByUserIdOrderByEndsAtDesc(USER))
                .willReturn(Optional.of(new NoShowSuspension(USER, end.minus(DAY), end)));
        faultsFrom(end);
        faultsFrom(NOW.minus(Duration.ofDays(7)), Duration.ofDays(2), DAY);

        assertThat(service.suspendedUntil(USER, NOW)).contains(end);
        NoShowStatusResponse status = service.status(USER);
        assertThat(status.suspendedUntil()).isEqualTo(end);
        assertThat(status.recentNoShows()).isEqualTo(2); // suspendido: todas las de la ventana
    }

    @Test
    void aPriorityUserIsExemptButTheFaultsAreStillCounted() {
        faultsAgo(Duration.ofDays(2), DAY, HOUR);
        faultsFrom(NOW.minus(Duration.ofDays(7)), Duration.ofDays(2), DAY, HOUR);
        given(priorities.currentPriority(USER)).willReturn(PriorityCategory.TEACHER);

        NoShowStatusResponse status = service.status(USER);

        assertThat(status.exempt()).isTrue();
        assertThat(status.suspendedUntil()).isNull();
        assertThat(status.recentNoShows()).isEqualTo(3);
    }

    @Test
    void theStatusCarriesTheRuleParametersAndDoesNotSave() {
        faultsAgo(Duration.ofDays(2), DAY);

        assertThat(service.status(USER)).isEqualTo(new NoShowStatusResponse(2, 3, 7, 24, null, false));
        verify(suspensions, org.mockito.Mockito.never()).saveAll(any());
    }
}
