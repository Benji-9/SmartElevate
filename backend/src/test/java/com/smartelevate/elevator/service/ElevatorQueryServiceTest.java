package com.smartelevate.elevator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartelevate.common.config.ConfigService;
import com.smartelevate.elevator.dto.BuildingResponse;
import com.smartelevate.elevator.model.Building;
import com.smartelevate.elevator.repository.BuildingRepository;
import com.smartelevate.elevator.repository.CoreRepository;
import com.smartelevate.elevator.repository.ElevatorRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class ElevatorQueryServiceTest {

    private final BuildingRepository buildings = mock(BuildingRepository.class);
    private final CoreRepository cores = mock(CoreRepository.class);
    private final ConfigService config = mock(ConfigService.class);
    private final ElevatorQueryService service =
            new ElevatorQueryService(mock(ElevatorRepository.class), buildings, cores, config);

    @Test
    void buildingsCarryTheConfiguredLowFloorsThreshold() {
        when(buildings.findAll(any(Sort.class))).thenReturn(List.of(new Building("LIMA", "Lima", -2, 10)));
        when(cores.findAll(any(Sort.class))).thenReturn(List.of());
        when(config.lowFloorsThreshold()).thenReturn(7);

        List<BuildingResponse> result = service.findBuildings();

        assertThat(result).singleElement().satisfies(b -> assertThat(b.lowFloorsThreshold()).isEqualTo(7));
    }
}
