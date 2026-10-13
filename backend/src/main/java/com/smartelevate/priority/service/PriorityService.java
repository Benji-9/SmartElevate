package com.smartelevate.priority.service;

import com.smartelevate.common.config.ConfigService;
import com.smartelevate.priority.dto.PendingTeacherResponse;
import com.smartelevate.priority.dto.PriorityUserResponse;
import com.smartelevate.priority.model.PriorityAudit;
import com.smartelevate.priority.model.PriorityCategory;
import com.smartelevate.priority.model.PriorityStatus;
import com.smartelevate.priority.model.UserPriority;
import com.smartelevate.priority.repository.PriorityAuditRepository;
import com.smartelevate.priority.repository.UserPriorityRepository;
import com.smartelevate.user.model.DeclaredUserType;
import com.smartelevate.user.model.Role;
import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prioridad vigente de los usuarios y su administración (ADR 0009). {@link #currentPriority} es el único punto que
 * usa la reserva para saber la prioridad: se evalúa acá, en el servidor, y nunca sale de un claim del JWT.
 * Todo cambio de rol o prioridad queda en {@code priority_audit}.
 */
@Service
@RequiredArgsConstructor
public class PriorityService {

    private final UserPriorityRepository priorities;
    private final PriorityAuditRepository audit;
    private final UserRepository users;
    private final ConfigService config;
    private final Clock clock;

    /**
     * Prioridad vigente: aprobada y no vencida. Si hay más de una, gana movilidad reducida. Al vencer, vuelve a
     * {@code NONE} sin ningún job: se evalúa al consultar.
     */
    @Transactional(readOnly = true)
    public PriorityCategory currentPriority(UUID userId) {
        Instant now = clock.instant();
        return priorities.findByUserIdAndStatus(userId, PriorityStatus.APPROVED).stream()
                .filter(p -> p.isCurrent(now))
                .map(UserPriority::getCategory)
                .max(Comparator.naturalOrder()) // REDUCED_MOBILITY > TEACHER > NONE
                .orElse(PriorityCategory.NONE);
    }

    @Transactional(readOnly = true)
    public List<PendingTeacherResponse> pendingTeachers() {
        return users.findPendingTeachers().stream()
                .map(u -> new PendingTeacherResponse(u.getId(), u.getFullName(), u.getEmail(), u.getLegajo(),
                        u.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PriorityUserResponse> priorityUsers() {
        Instant now = clock.instant();
        List<UserPriority> current = priorities.findByStatus(PriorityStatus.APPROVED).stream()
                .filter(p -> p.isCurrent(now))
                .toList();
        var byId = new java.util.HashMap<UUID, User>();
        users.findAllById(current.stream().map(UserPriority::getUserId).toList())
                .forEach(u -> byId.put(u.getId(), u));
        return current.stream()
                .map(p -> {
                    User u = byId.get(p.getUserId());
                    return new PriorityUserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getLegajo(),
                            p.getCategory());
                })
                .sorted(Comparator.comparing(PriorityUserResponse::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Aprueba a un docente declarado. Con {@code priority_teacher_validity} definido, la prioridad vence. */
    @Transactional
    public void approveTeacher(UUID actorId, UUID userId) {
        User user = declaredTeacher(userId);
        Instant now = clock.instant();
        UserPriority p = teacherRow(user.getId());
        if (p.isCurrent(now)) {
            throw new InvalidPriorityOperationException("El docente ya tiene la prioridad aprobada");
        }
        Instant expiresAt = config.priorityTeacherValidity().map(now::plus).orElse(null);
        p.decide(PriorityStatus.APPROVED, expiresAt, now, actorId);
        priorities.save(p);
        record(actorId, userId, "TEACHER_APPROVED", expiresAt == null ? null : "expires " + expiresAt);
    }

    /** Rechaza a un docente declarado: sale de la cola y sigue como usuario común. */
    @Transactional
    public void rejectTeacher(UUID actorId, UUID userId) {
        User user = declaredTeacher(userId);
        Instant now = clock.instant();
        UserPriority p = teacherRow(user.getId());
        if (p.isCurrent(now)) {
            throw new InvalidPriorityOperationException(
                    "El docente tiene la prioridad aprobada: para quitarla hay que revocarla");
        }
        p.decide(PriorityStatus.REJECTED, null, now, actorId);
        priorities.save(p);
        record(actorId, userId, "TEACHER_REJECTED", null);
    }

    /** Quita la prioridad vigente (cualquier categoría). Queda como rechazada, no vuelve a la cola. */
    @Transactional
    public void revoke(UUID actorId, UUID userId) {
        Instant now = clock.instant();
        List<UserPriority> current = priorities.findByUserIdAndStatus(userId, PriorityStatus.APPROVED).stream()
                .filter(p -> p.isCurrent(now))
                .toList();
        if (current.isEmpty()) {
            throw new PriorityNotFoundException("El usuario no tiene una prioridad vigente");
        }
        for (UserPriority p : current) {
            p.decide(PriorityStatus.REJECTED, null, now, actorId);
            record(actorId, userId, "PRIORITY_REVOKED", p.getCategory().name());
        }
        priorities.saveAll(current);
    }

    /** Asigna el rol. Nadie puede cambiarse el suyo: así siempre queda al menos un ADMIN. */
    @Transactional
    public void assignRole(UUID actorId, UUID userId, Role role) {
        if (actorId.equals(userId)) {
            throw new InvalidPriorityOperationException("No podés cambiar tu propio rol");
        }
        User user = users.findById(userId).orElseThrow(() -> new PriorityNotFoundException("Usuario inexistente"));
        Role previous = user.getRole();
        user.assignRole(role);
        users.save(user);
        record(actorId, userId, "ROLE_CHANGED", previous + "->" + role);
    }

    private User declaredTeacher(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> new PriorityNotFoundException("Usuario inexistente"));
        if (user.getDeclaredUserType() != DeclaredUserType.TEACHER) {
            throw new InvalidPriorityOperationException("El usuario no se declaró docente");
        }
        return user;
    }

    private UserPriority teacherRow(UUID userId) {
        return priorities.findByUserIdAndCategory(userId, PriorityCategory.TEACHER)
                .orElseGet(() -> new UserPriority(userId, PriorityCategory.TEACHER));
    }

    private void record(UUID actorId, UUID targetId, String action, String detail) {
        audit.save(new PriorityAudit(actorId, targetId, action, detail, clock.instant()));
    }
}
