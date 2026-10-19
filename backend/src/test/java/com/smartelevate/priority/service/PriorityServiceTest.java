package com.smartelevate.priority.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.smartelevate.common.config.ConfigService;
import com.smartelevate.common.time.CampusTime;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PriorityServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();

    private final UserPriorityRepository priorities = mock(UserPriorityRepository.class);
    private final PriorityAuditRepository audit = mock(PriorityAuditRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ConfigService config = mock(ConfigService.class);
    private final PriorityService service = at(NOW);

    private static final ZoneId CAMPUS = ZoneId.of("America/Argentina/Buenos_Aires");

    private PriorityService at(Instant now) {
        return new PriorityService(priorities, audit, users, config,
                new CampusTime(Clock.fixed(now, ZoneOffset.UTC), CAMPUS));
    }

    private Instant approvedExpiry(PriorityService atTime, User u) {
        atTime.approveTeacher(ADMIN, u.getId());
        ArgumentCaptor<UserPriority> saved = ArgumentCaptor.forClass(UserPriority.class);
        verify(priorities, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        return saved.getValue().getExpiresAt();
    }

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

    private static final List<LocalDate> SEMESTER_ENDS = List.of(LocalDate.of(2026, 7, 31),
            LocalDate.of(2026, 12, 19), LocalDate.of(2027, 7, 31));

    @Test
    void approveTeacherGrantsPriorityUntilTheEndOfTheCurrentSemesterAndAudits() {
        User u = user(DeclaredUserType.TEACHER);
        given(priorities.findByUserIdAndCategory(u.getId(), PriorityCategory.TEACHER)).willReturn(Optional.empty());
        given(config.semesterEndDates()).willReturn(SEMESTER_ENDS);

        service.approveTeacher(ADMIN, u.getId());

        ArgumentCaptor<UserPriority> saved = ArgumentCaptor.forClass(UserPriority.class);
        verify(priorities).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PriorityStatus.APPROVED);
        // Fin del 19/12 en Buenos Aires (UTC-3) = 20/12 03:00 UTC.
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(Instant.parse("2026-12-20T03:00:00Z"));
        assertThat(saved.getValue().getDecidedBy()).isEqualTo(ADMIN);
        assertThat(auditedAction()).isEqualTo("TEACHER_APPROVED");
    }

    // Borde del cambio de cuatrimestre: el día del cierre (hasta las 23:59 de Buenos Aires, ya 20/12 en UTC)
    // todavía es el cuatrimestre que cierra; desde las 00:00 del 20/12 es el siguiente.
    @ParameterizedTest
    @CsvSource({
            "2026-12-19T03:00:00Z, 2026-12-20T03:00:00Z",
            "2026-12-20T02:59:59Z, 2026-12-20T03:00:00Z",
            "2026-12-20T03:00:00Z, 2027-08-01T03:00:00Z",
            "2026-07-31T12:00:00Z, 2026-08-01T03:00:00Z",
            "2026-08-01T03:00:00Z, 2026-12-20T03:00:00Z"})
    void approvalExpiresAtTheEndOfTheSemesterInCourse(Instant now, Instant expected) {
        User u = user(DeclaredUserType.TEACHER);
        given(priorities.findByUserIdAndCategory(u.getId(), PriorityCategory.TEACHER)).willReturn(Optional.empty());
        given(config.semesterEndDates()).willReturn(SEMESTER_ENDS);

        assertThat(approvedExpiry(at(now), u)).isEqualTo(expected);
    }

    @Test
    void anApprovalExpiresAtTheEndOfTheClosingDayAndCanBeRenewed() {
        User u = user(DeclaredUserType.TEACHER);
        UserPriority p = approved(u.getId(), PriorityCategory.TEACHER, Instant.parse("2026-12-20T03:00:00Z"));
        given(priorities.findByUserIdAndCategory(u.getId(), PriorityCategory.TEACHER)).willReturn(Optional.of(p));
        given(config.semesterEndDates()).willReturn(SEMESTER_ENDS);

        assertThat(at(Instant.parse("2026-12-20T02:59:59Z")).currentPriority(u.getId()))
                .isEqualTo(PriorityCategory.TEACHER);
        assertThatThrownBy(() -> at(Instant.parse("2026-12-20T02:59:59Z")).approveTeacher(ADMIN, u.getId()))
                .isInstanceOf(InvalidPriorityOperationException.class);

        PriorityService nextSemester = at(Instant.parse("2026-12-20T03:00:00Z"));
        assertThat(nextSemester.currentPriority(u.getId())).isEqualTo(PriorityCategory.NONE);
        assertThat(approvedExpiry(nextSemester, u)).isEqualTo(Instant.parse("2027-08-01T03:00:00Z"));
    }

    @Test
    void approvalFailsClearlyWhenNoSemesterEndIsLoadedFromToday() {
        User u = user(DeclaredUserType.TEACHER);
        given(priorities.findByUserIdAndCategory(u.getId(), PriorityCategory.TEACHER)).willReturn(Optional.empty());
        given(config.semesterEndDates()).willReturn(List.of(LocalDate.of(2026, 7, 31)));

        assertThatThrownBy(() -> service.approveTeacher(ADMIN, u.getId()))
                .isInstanceOf(InvalidPriorityOperationException.class)
                .hasMessageContaining("semester_end_dates");
        verify(priorities, never()).save(any());
        verify(audit, never()).save(any());
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

    // --- tipo de usuario (#262) ---

    private User verified(DeclaredUserType type) {
        User u = user(type);
        u.verifyEmail();
        return u;
    }

    private List<String> auditedDetails() {
        ArgumentCaptor<PriorityAudit> captor = ArgumentCaptor.forClass(PriorityAudit.class);
        verify(audit, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream().map(a -> a.getAction() + ":" + a.getDetail()).toList();
    }

    @Test
    void studentToTeacherChangesTheTypeAndGrantsThePriorityDirectly() {
        given(config.semesterEndDates()).willReturn(List.of(LocalDate.of(2026, 12, 31)));
        User u = verified(DeclaredUserType.STUDENT);

        service.changeDeclaredType(ADMIN, u.getId(), DeclaredUserType.TEACHER);

        assertThat(u.getDeclaredUserType()).isEqualTo(DeclaredUserType.TEACHER);
        ArgumentCaptor<UserPriority> saved = ArgumentCaptor.forClass(UserPriority.class);
        verify(priorities).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PriorityStatus.APPROVED);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(Instant.parse("2027-01-01T03:00:00Z"));
        assertThat(auditedDetails()).containsExactly("DECLARED_TYPE_CHANGED:STUDENT->TEACHER",
                "TEACHER_APPROVED:expires 2027-01-01T03:00:00Z");
    }

    @Test
    void studentToTeacherAfterARejectionOrAnExpiredApprovalGetsThePriorityAgain() {
        given(config.semesterEndDates()).willReturn(List.of(LocalDate.of(2026, 12, 31)));
        User u = verified(DeclaredUserType.STUDENT);
        UserPriority rejected = new UserPriority(u.getId(), PriorityCategory.TEACHER);
        rejected.decide(PriorityStatus.REJECTED, null, NOW.minusSeconds(3600), ADMIN);
        given(priorities.findByUserIdAndCategory(u.getId(), PriorityCategory.TEACHER))
                .willReturn(Optional.of(rejected));

        service.changeDeclaredType(ADMIN, u.getId(), DeclaredUserType.TEACHER);

        assertThat(rejected.isCurrent(NOW)).isTrue();
        verify(priorities).save(rejected);
    }

    @Test
    void studentToTeacherWithoutASemesterEndFailsAndChangesNothingAudited() {
        given(config.semesterEndDates()).willReturn(List.of());
        User u = verified(DeclaredUserType.STUDENT);

        assertThatThrownBy(() -> service.changeDeclaredType(ADMIN, u.getId(), DeclaredUserType.TEACHER))
                .isInstanceOf(InvalidPriorityOperationException.class)
                .hasMessageContaining("cierre de cuatrimestre");
        verify(priorities, never()).save(any());
    }

    @Test
    void teacherToStudentRevokesTheCurrentTeacherPriority() {
        User u = verified(DeclaredUserType.TEACHER);
        UserPriority current = new UserPriority(u.getId(), PriorityCategory.TEACHER);
        current.decide(PriorityStatus.APPROVED, NOW.plusSeconds(3600), NOW.minusSeconds(3600), ADMIN);
        given(priorities.findByUserIdAndCategory(u.getId(), PriorityCategory.TEACHER))
                .willReturn(Optional.of(current));

        service.changeDeclaredType(ADMIN, u.getId(), DeclaredUserType.STUDENT);

        assertThat(u.getDeclaredUserType()).isEqualTo(DeclaredUserType.STUDENT);
        assertThat(current.isCurrent(NOW)).isFalse();
        assertThat(current.getStatus()).isEqualTo(PriorityStatus.REJECTED);
        verify(priorities).save(current);
        assertThat(auditedDetails())
                .containsExactly("DECLARED_TYPE_CHANGED:TEACHER->STUDENT", "PRIORITY_REVOKED:TEACHER");
    }

    @Test
    void teacherToStudentWithoutAPriorityOnlyChangesTheType() {
        User u = verified(DeclaredUserType.TEACHER);

        service.changeDeclaredType(ADMIN, u.getId(), DeclaredUserType.STUDENT);

        assertThat(auditedDetails()).containsExactly("DECLARED_TYPE_CHANGED:TEACHER->STUDENT");
        verify(priorities, never()).save(any());
    }

    @Test
    void changingTheTypeRequiresAVerifiedEmail() {
        User u = user(DeclaredUserType.STUDENT);

        assertThatThrownBy(() -> service.changeDeclaredType(ADMIN, u.getId(), DeclaredUserType.TEACHER))
                .isInstanceOf(InvalidPriorityOperationException.class)
                .hasMessageContaining("verificó su email");
        assertThat(u.getDeclaredUserType()).isEqualTo(DeclaredUserType.STUDENT);
        verify(audit, never()).save(any());
    }

    @Test
    void changingToTheSameTypeIsRejected() {
        User u = verified(DeclaredUserType.TEACHER);

        assertThatThrownBy(() -> service.changeDeclaredType(ADMIN, u.getId(), DeclaredUserType.TEACHER))
                .isInstanceOf(InvalidPriorityOperationException.class);
        verify(audit, never()).save(any());
    }

    @Test
    void changingTheTypeOfAnUnknownUserIsNotFound() {
        assertThatThrownBy(() -> service.changeDeclaredType(ADMIN, UUID.randomUUID(), DeclaredUserType.TEACHER))
                .isInstanceOf(PriorityNotFoundException.class);
    }
}
