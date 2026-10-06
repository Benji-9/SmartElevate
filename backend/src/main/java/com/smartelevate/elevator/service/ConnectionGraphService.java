package com.smartelevate.elevator.service;

import com.smartelevate.elevator.model.CoreConnection;
import com.smartelevate.elevator.repository.CoreConnectionRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Edificio alternativo (asignacion.md): BFS sobre el grafo de núcleos, armado en cada llamada desde
 * {@code core_connection}, así que editar una conexión en la base cambia el resultado sin tocar código.
 */
@Service
@RequiredArgsConstructor
public class ConnectionGraphService {

    private final CoreConnectionRepository connections;

    /**
     * Núcleo alcanzable a {@code hops} saltos. Un tiempo de caminata NULL no se suma y deja
     * {@code walkingTimeKnown = false} (#22).
     */
    public record AlternativeCore(String coreCode, int hops, int walkingTimeSeconds, boolean walkingTimeKnown) {
    }

    /**
     * Núcleos alcanzables desde {@code originCore} moviéndose por el piso {@code floor} (sin el origen),
     * ordenados por saltos. Con {@code accessibleOnly} solo se usan conexiones con {@code accessible = TRUE}:
     * NULL es "a confirmar" y no se ofrece.
     */
    public List<AlternativeCore> alternatives(String originCore, int floor, boolean accessibleOnly) {
        Map<String, List<CoreConnection>> edges = new HashMap<>();
        for (CoreConnection connection : connections.findAll()) {
            if (floor < connection.getFromFloor() || floor > connection.getToFloor()) {
                continue;
            }
            if (accessibleOnly && !Boolean.TRUE.equals(connection.getAccessible())) {
                continue;
            }
            edges.computeIfAbsent(connection.getCoreA().getCode(), k -> new ArrayList<>()).add(connection);
            edges.computeIfAbsent(connection.getCoreB().getCode(), k -> new ArrayList<>()).add(connection);
        }

        // BFS: el orden de inserción es el orden por saltos.
        Map<String, AlternativeCore> reached = new LinkedHashMap<>();
        AlternativeCore origin = new AlternativeCore(originCore, 0, 0, true);
        reached.put(originCore, origin);
        Queue<AlternativeCore> queue = new ArrayDeque<>(List.of(origin));
        while (!queue.isEmpty()) {
            AlternativeCore current = queue.poll();
            for (CoreConnection connection : edges.getOrDefault(current.coreCode(), List.of())) {
                String next = connection.getCoreA().getCode().equals(current.coreCode())
                        ? connection.getCoreB().getCode()
                        : connection.getCoreA().getCode();
                if (reached.containsKey(next)) {
                    continue;
                }
                Integer time = connection.getWalkingTimeSeconds();
                AlternativeCore alternative = new AlternativeCore(next, current.hops() + 1,
                        current.walkingTimeSeconds() + (time == null ? 0 : time),
                        current.walkingTimeKnown() && time != null);
                reached.put(next, alternative);
                queue.add(alternative);
            }
        }
        reached.remove(originCore);
        return List.copyOf(reached.values());
    }
}
