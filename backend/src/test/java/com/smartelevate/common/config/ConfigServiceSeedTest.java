package com.smartelevate.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/** El seed de V1__init.sql, aplicado por Flyway sobre H2, carga y pasa la validación con los valores de las reglas. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ConfigService.class)
class ConfigServiceSeedTest {

    @Autowired
    private ConfigService config;

    @Test
    void seedMatchesTheBusinessRules() {
        assertThat(config.operatingHoursStart()).isEqualTo(LocalTime.of(7, 0));
        assertThat(config.operatingHoursEnd()).isEqualTo(LocalTime.of(22, 30));
        assertThat(config.slotDuration()).isEqualTo(Duration.ofMinutes(2));
        assertThat(config.turnCapacity()).isEqualTo(10);
        assertThat(config.bookingOpensBefore()).isEqualTo(Duration.ofMinutes(30));
        assertThat(config.bookingClosesBefore()).isEqualTo(Duration.ofMinutes(2));
        assertThat(config.maxActiveBookingsPerUser()).isEqualTo(1);
        assertThat(config.maxDailyBookingsPerUser()).isEqualTo(8);
        assertThat(config.cancellationDeadlineBefore()).isEqualTo(Duration.ofMinutes(1));
        assertThat(config.checkInOpensBefore()).isEqualTo(Duration.ofMinutes(1));
        assertThat(config.checkInClosesAfter()).isEqualTo(Duration.ofMinutes(2));
        assertThat(config.noShowAfter()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.noShowSuspensionThreshold()).isEqualTo(3);
        assertThat(config.noShowSuspensionWindow()).isEqualTo(Duration.ofDays(7));
        assertThat(config.noShowSuspensionDuration()).isEqualTo(Duration.ofHours(24));
        assertThat(config.prioritySeatsPerDeparture()).isEqualTo(2);
        assertThat(config.prioritySeatsReleaseBefore()).isEqualTo(Duration.ofMinutes(1));
        assertThat(config.qrRotation()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.boardingTime()).isEqualTo(Duration.ofSeconds(14));
        assertThat(config.timePerFloor()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void parametersToBeDefinedExistButFailWhenRequested() {
        assertThatThrownBy(config::cancellationWaitTolerance).hasMessageContaining("todavía no está definido");
        assertThatThrownBy(config::priorityAging).hasMessageContaining("todavía no está definido");
        assertThatThrownBy(config::lowFloorsThreshold).hasMessageContaining("todavía no está definido");
    }
}
