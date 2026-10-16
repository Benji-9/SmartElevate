package com.smartelevate.turn.service;

import com.smartelevate.common.db.PostgresTestcontainersConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Las mismas carreras contra Postgres real (ADR 0010). Sin Docker se saltea; en CI corre siempre. */
@SpringBootTest
@Import({PostgresTestcontainersConfig.class, ReservationConcurrencyTestBase.FixedClockConfig.class})
@Testcontainers(disabledWithoutDocker = true)
class ElevatorOutageRacePostgresTest extends ElevatorOutageRaceTestBase {
}
