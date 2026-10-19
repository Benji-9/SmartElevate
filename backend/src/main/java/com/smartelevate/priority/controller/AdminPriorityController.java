package com.smartelevate.priority.controller;

import com.smartelevate.common.config.OpenApiConfig;
import com.smartelevate.common.error.ApiError;
import com.smartelevate.common.security.CurrentUser;
import com.smartelevate.priority.dto.AdminUserPage;
import com.smartelevate.priority.dto.AssignDeclaredTypeRequest;
import com.smartelevate.priority.dto.AssignRoleRequest;
import com.smartelevate.priority.dto.PendingTeacherResponse;
import com.smartelevate.priority.dto.PriorityUserResponse;
import com.smartelevate.priority.service.PriorityService;
import com.smartelevate.priority.service.UserSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gestión de roles y prioridad. Solo ADMIN: el rol se lee de la base en cada request, no del token. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@Tag(name = "admin-priority", description = "Roles y prioridad de usuarios (solo ADMIN)")
public class AdminPriorityController {

    private final PriorityService service;
    private final UserSearchService userSearch;

    @GetMapping("/teachers/pending")
    @Operation(summary = "Docentes declarados que esperan aprobación")
    public List<PendingTeacherResponse> pendingTeachers() {
        return service.pendingTeachers();
    }

    @PostMapping("/teachers/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Aprueba a un docente: pasa a tener prioridad TEACHER")
    @ApiResponse(responseCode = "404", description = "Usuario inexistente",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "No se declaró docente o ya está aprobado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public void approve(@AuthenticationPrincipal CurrentUser admin, @PathVariable UUID id) {
        service.approveTeacher(admin.id(), id);
    }

    @PostMapping("/teachers/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Rechaza a un docente: sigue como usuario común")
    @ApiResponse(responseCode = "404", description = "Usuario inexistente",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "No se declaró docente o ya tiene la prioridad aprobada",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public void reject(@AuthenticationPrincipal CurrentUser admin, @PathVariable UUID id) {
        service.rejectTeacher(admin.id(), id);
    }

    @GetMapping("/priority-users")
    @Operation(summary = "Usuarios con prioridad vigente")
    public List<PriorityUserResponse> priorityUsers() {
        return service.priorityUsers();
    }

    @DeleteMapping("/priority-users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Quita la prioridad vigente de un usuario")
    @ApiResponse(responseCode = "404", description = "El usuario no tiene una prioridad vigente",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public void revoke(@AuthenticationPrincipal CurrentUser admin, @PathVariable UUID id) {
        service.revoke(admin.id(), id);
    }

    @GetMapping("/users")
    @Operation(summary = "Busca usuarios por email, nombre o legajo (contiene, paginado por cursor)")
    @ApiResponse(responseCode = "400", description = "Búsqueda demasiado corta o cursor inválido",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AdminUserPage searchUsers(@RequestParam(defaultValue = "") String query, @RequestParam(required = false) UUID cursor) {
        return userSearch.search(query, cursor);
    }

    @PutMapping("/users/{id}/role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Asigna el rol de un usuario (USER, REVIEWER, ADMIN)")
    @ApiResponse(responseCode = "404", description = "Usuario inexistente",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "No se puede cambiar el rol propio",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public void assignRole(@AuthenticationPrincipal CurrentUser admin, @PathVariable UUID id,
                           @Valid @RequestBody AssignRoleRequest request) {
        service.assignRole(admin.id(), id, request.role());
    }

    @PutMapping("/users/{id}/declared-type")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cambia el tipo de usuario (STUDENT, TEACHER) de una cuenta con email verificado",
            description = "Pasar a docente le da la prioridad de docente directamente, hasta el fin del cuatrimestre en "
                    + "curso. Pasar a estudiante le revoca la prioridad de docente si la tenía vigente.")
    @ApiResponse(responseCode = "404", description = "Usuario inexistente",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Email sin verificar, el usuario ya tiene ese tipo o falta cargar el cierre del cuatrimestre",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public void changeDeclaredType(@AuthenticationPrincipal CurrentUser admin, @PathVariable UUID id,
                                   @Valid @RequestBody AssignDeclaredTypeRequest request) {
        service.changeDeclaredType(admin.id(), id, request.declaredUserType());
    }
}
