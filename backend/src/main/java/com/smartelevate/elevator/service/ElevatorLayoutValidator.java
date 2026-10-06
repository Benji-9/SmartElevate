package com.smartelevate.elevator.service;

import com.smartelevate.elevator.model.Building;
import com.smartelevate.elevator.model.Core;
import com.smartelevate.elevator.model.CoreConnection;
import com.smartelevate.elevator.model.Elevator;
import com.smartelevate.elevator.repository.CoreConnectionRepository;
import com.smartelevate.elevator.repository.ElevatorRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Valida al arrancar los ascensores y las conexiones de la base (ADR 0010); si algo no cierra, la app no levanta.
 * <ul>
 *   <li>Cada piso de un ascensor está dentro del rango de su edificio.</li>
 *   <li>Cada piso de una conexión lo sirven los dos núcleos (al menos un ascensor activo de cada uno).</li>
 * </ul>
 */
@Component
public class ElevatorLayoutValidator {

    public ElevatorLayoutValidator(ElevatorRepository elevators, CoreConnectionRepository connections) {
        validate(elevators.findAll(), connections.findAll());
    }

    static void validate(List<Elevator> elevators, List<CoreConnection> connections) {
        Map<String, Set<Integer>> servedByCore = new HashMap<>();
        for (Elevator elevator : elevators) {
            Building building = elevator.getCore().getBuilding();
            for (int floor : elevator.getFloors()) {
                check(floor >= building.getMinFloor() && floor <= building.getMaxFloor(),
                        "el ascensor %s sirve el piso %d, fuera del rango de %s (%d a %d)".formatted(
                                elevator.getCode(), floor, building.getName(),
                                building.getMinFloor(), building.getMaxFloor()));
            }
            if (elevator.isActive()) {
                servedByCore.computeIfAbsent(elevator.getCore().getCode(), k -> new HashSet<>())
                        .addAll(elevator.getFloors());
            }
        }
        for (CoreConnection connection : connections) {
            for (int floor = connection.getFromFloor(); floor <= connection.getToFloor(); floor++) {
                for (Core core : List.of(connection.getCoreA(), connection.getCoreB())) {
                    check(servedByCore.getOrDefault(core.getCode(), Set.of()).contains(floor),
                            "la conexión %s–%s cubre el piso %d, que ningún ascensor activo de %s sirve".formatted(
                                    connection.getCoreA().getCode(), connection.getCoreB().getCode(),
                                    floor, core.getCode()));
                }
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Configuración de ascensores inválida: " + message);
        }
    }
}
