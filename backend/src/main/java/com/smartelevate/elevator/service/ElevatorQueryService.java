package com.smartelevate.elevator.service;

import com.smartelevate.elevator.dto.BuildingResponse;
import com.smartelevate.elevator.dto.ElevatorResponse;
import com.smartelevate.elevator.model.Core;
import com.smartelevate.elevator.model.Elevator;
import com.smartelevate.elevator.repository.BuildingRepository;
import com.smartelevate.elevator.repository.CoreRepository;
import com.smartelevate.elevator.repository.ElevatorRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lectura de edificios, núcleos y ascensores para la API. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ElevatorQueryService {

    private static final Comparator<Elevator> BY_BUILDING_CORE_CODE = Comparator
            .comparing((Elevator e) -> e.getCore().getBuilding().getCode())
            .thenComparing(e -> e.getCore().getCode())
            .thenComparing(Elevator::getCode);

    private final ElevatorRepository elevators;
    private final BuildingRepository buildings;
    private final CoreRepository cores;

    /** Ascensores activos, opcionalmente de un edificio y/o que paren en un piso (null = sin filtro). */
    // ponytail: filtra en memoria (son 18 ascensores); pasar a una query si la tabla crece.
    public List<ElevatorResponse> findActive(String buildingCode, Integer floor) {
        return elevators.findAll().stream()
                .filter(Elevator::isActive)
                .filter(e -> buildingCode == null || e.getCore().getBuilding().getCode().equals(buildingCode))
                .filter(e -> floor == null || e.getFloors().contains(floor))
                .sorted(BY_BUILDING_CORE_CODE)
                .map(e -> new ElevatorResponse(e.getCode(), e.getCore().getCode(),
                        e.getCore().getBuilding().getCode(), e.getUsage(), e.isActive(),
                        e.getFloors().stream().sorted().toList()))
                .toList();
    }

    public List<BuildingResponse> findBuildings() {
        Map<String, List<BuildingResponse.CoreResponse>> coresByBuilding = cores.findAll(Sort.by("code")).stream()
                .collect(Collectors.groupingBy(c -> c.getBuilding().getCode(),
                        Collectors.mapping((Core c) -> new BuildingResponse.CoreResponse(c.getCode()),
                                Collectors.toList())));
        return buildings.findAll(Sort.by("code")).stream()
                .map(b -> new BuildingResponse(b.getCode(), b.getName(), b.getMinFloor(), b.getMaxFloor(),
                        coresByBuilding.getOrDefault(b.getCode(), List.of())))
                .toList();
    }
}
