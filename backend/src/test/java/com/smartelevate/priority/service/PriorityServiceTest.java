package com.smartelevate.priority.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.smartelevate.common.config.ConfigService;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PriorityServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();

    private final UserPriorityRepository priorities = mock(UserPriorityRepository.class);
    private final PriorityAuditRepository audit = mock(PriorityAuditRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ConfigService config = mock(ConfigService.class);
    private final PriorityService service =
            new PriorityService(priorities, audit, users, config, Clock.fixed(NOW, ZoneOffset.UTC));

    private User user(DeclaredUserType type) {
        User u = new User("Ana Pérez", "ana@uade.edu.ar", "1111111", "x", type, NOW);
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(u, "id", id);
        given(users.findById(id)).willReturn(Optional.of(u));
        return u;
    }

    private UserPriority approved(UUID userId, PriorityCategory category, Instant expiresAt) {
        UserPriority p = new UserPriority(userId, category);
        p.decide(PriorityStatus.APPROVED, expiresAt, NOW.minusSeconds(3600), ADMIN);
        given(priorities.findByUserIdAndStatus(userId, PriorityStatus.APPROVED)).willReturn(List.of(p));
        return p;
    }

    private String auditedAction() {
        ArgumentCaptor<PriorityAudit> captor = ArgumentCaptor.forClass(PriorityAudit.class);
        verify(audit).save(captor.capture());
        return captor.getValue().getAction();
    }

    // --- prioridad vigente ---

    @Test
    void noRowsMeansCommonUser() {
        assertThat(service.currentPriority(UUID.randomUUID())).isEqualTo(PriorityCategory.NONE);
    }

    @Test
    void approvedAndNotExpiredIsCurrent() {
        UUID id = UUID.randomUUID();
        approved(id, PriorityCategory.TEACHER, NOW.plusSeconds(1));
        assertThat(service.currentPriority(id)).isEqualTo(PriorityCategory.TEACHER);
    }

    @Test
    void withoutExpirationItNeverExpires() {
        UUID id = UUID.randomUUID();
        approved(id, PriorityCategory.REDUCED_MOBILITY, null);
        assertThat(service.currentPriority(id)).isEqualTo(PriorityCategory.REDUCED_MOBILITY);
    }

    @Test
    void expiredPriorityIsCommonUserAgain() {
        UUID id = UUID.randomUUID();
        approved(id, PriorityCategory.TEACHER, NOW); // vence en este mismo instante: ya no vale
        assertThat(service.currentPriority(id)).isEqualTo(PriorityCategory.NONE);
    }

    @Test
    void reducedMobilityWinsOverTeacher() {
        UUID id = UUID.randomUUID();
        UserPriority t = new UserPriority(id, PriorityCategory.TEACHER);
        t.decide(PriorityStatus.APPROVED, null, NOW, ADMIN);
        UserPriority m = new UserPriority(id, PriorityCategory.REDUCED_MOBILITY);
        m.decide(PriorityStatus.APPROVED, null, NOW, ADMIN);
        given(priorities.findByUserIdAndStatus(id, PriorityStatus.APPROVED)).willReturn(List.of(t, m));
        assertThat(service.currentPriority(id)).isEqualTo(PriorityCategory.REDUCED_MOBILITY);
    }

    // --- docentes ---

    @Test
    void approveTeacherGrantsPriorityAndAudits() {
        User u = user(DeclaredUserType.TEACHER);
        given(priorities.findByUserIdAndCategory(u.getId(), PriorityCategory.TEACHER)).willReturn(Optional.empty());
        given(config.priorityTeacherValidity()).willReturn(Optional.empty());

        service.approveTeacher(ADMIN, u.getId());

        ArgumentCaptor<UserPriority> saved = ArgumentCaptor.forClass(UserPriority.class);
        verify(priorities).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PriorityStatus.APPROVED);
        assertThat(saved.getValue().getExpiresAt()).isNull();
        assertThat(saved.getValue().getDecidedBy()).isEqualTo(ADMIN);
        assertThat(auditedAction()).isEqualTo("TEACHER_APPROVED");
    }

    @Test
    void approveTeacherExpiresWhenValidityIsConfigured() {
        User u = user(DeclaredUserType.TEACHER);
        given(priorities.findByUserIdAndCategory(u.getId(), PriorityCategory.TEACHER)).willReturn(Optional.empty());
        given(config.priorityTeacherValidity()).willReturn(Optional.of(Duration.ofDays(180)));

        service.approveTeacher(ADMIN, u.getId());

        ArgumentCaptor<UserPriority> saved = ArgumentCaptor.forClass(UserPriority.class);
        verify(priorities).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(180)));
    }

    @Test
    void approveRequiresDeclaredTeacher() {
        User u = user(DeclaredUserType.STUDENT);
        assertThatThrownBy(() -> service.approveTeacher(ADMIN, u.getId()))
                .isInstanceOf(InvalidPriorityOperationException.class);
        verify(priorities, never()).save(any());
        verify(audit, never()).save(any());
    }

    @Test
    void approveUnknownUserIsNotFound() {
        assertThatThrownBy(() -> service.approveTeacher(ADMIN, UUID.randomUUID()))
                .isInstanceOf(PriorityNotFoundException.class);
    }

    @Test
    void approveTwiceIsConflict() {
        User u = user(DeclaredUserType.TEACHER);
        UserPriority p = new UserPriority(u.getId(), PriorityCategory.TEACHER);
        p.decide(PriorityStatus.APPROVED, null, NOW, ADMIN);
        given(priorities.findByUserIdAndCategory(u.getId(), PriorityCategory.TEACHER)).willReturn(Optional.of(p));
        assertThatThrownBy(() -> service.approveTeacher(ADMIN, u.getId()))
                .isInstanceOf(InvalidPriorityOperationException.class);
    }

    @Test
    void rejectTeacherLeavesTheQueueAsCommonUser() {
        User u = user(DeclaredUserType.TEACHER);
        given(priorities.findByUserIdAndCategory(u.getId(), PriorityCategory.TEACHER)).willReturn(Optional.empty());

        service.rejectTeacher(ADMIN, u.getId());

        ArgumentCaptor<UserPriority> saved = ArgumentCaptor.forClass(UserPriority.class);
        verify(priorities).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PriorityStatus.REJECTED);
        assertThat(auditedAction()).isEqualTo("TEACHER_REJECTED");
    }

    @Test
    void rejectingAnApprovedTeacherIsConflict() {
        User u = user(DeclaredUserType.TEACHER);
        UserPriority p = new UserPriority(u.getId(), PriorityCategory.TEACHER);
        p.decide(PriorityStatus.APPROVED, null, NOW, ADMIN);
        given(priorities.findByUserIdAndCategory(u.getId(), PriorityCategory.TEACHER)).willReturn(Optional.of(p));
        assertThatThrownBy(() -> service.rejectTeacher(ADMIN, u.getId()))
                .isInstanceOf(InvalidPriorityOperationException.class);
    }

    // --- revocar ---

    @Test
    void revokeTurnsCurrentPriorityIntoRejectedAndAudits() {
        UUID id = UUID.randomUUID();
        UserPriority p = approved(id, PriorityCategory.TEACHER, null);

        service.revoke(ADMIN, id);

        assertThat(p.getStatus()).isEqualTo(PriorityStatus.REJECTED);
        assertThat(auditedAction()).isEqualTo("PRIORITY_REVOKED");
    }

    @Test
    void revokeWithoutCurrentPriorityIsNotFound() {
        UUID id = UUID.randomUUID();
        approved(id, PriorityCategory.TEACHER, NOW.minusSeconds(1)); // vencida
        assertThatThrownBy(() -> service.revoke(ADMIN, id)).isInstanceOf(PriorityNotFoundException.class);
        verify(audit, never()).save(any());
    }

    // --- roles ---

    @Test
    void assignRoleChangesAndAudits() {
        User u = user(DeclaredUserType.STUDENT);

        service.assignRole(ADMIN, u.getId(), Role.REVIEWER);

        assertThat(u.getRole()).isEqualTo(Role.REVIEWER);
        ArgumentCaptor<PriorityAudit> captor = ArgumentCaptor.forClass(PriorityAudit.class);
        verify(audit).save(captor.capture());
        assertThat(captor.getValue().getDetail()).isEqualTo("USER->REVIEWER");
        assertThat(captor.getValue().getActorId()).isEqualTo(ADMIN);
    }

    @Test
    void nobodyChangesTheirOwnRole() {
        assertThatThrownBy(() -> service.assignRole(ADMIN, ADMIN, Role.USER))
                .isInstanceOf(InvalidPriorityOperationException.class);
        verify(audit, never()).save(any());
    }

    @Test
    void assignRoleToUnknownUserIsNotFound() {
        assertThatThrownBy(() -> service.assignRole(ADMIN, UUID.randomUUID(), Role.ADMIN))
                .isInstanceOf(PriorityNotFoundException.class);
    }
}
