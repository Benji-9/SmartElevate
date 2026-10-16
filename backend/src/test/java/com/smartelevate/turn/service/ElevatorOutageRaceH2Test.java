package com.smartelevate.turn.service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(ReservationConcurrencyTestBase.FixedClockConfig.class)
class ElevatorOutageRaceH2Test extends ElevatorOutageRaceTestBase {
}
