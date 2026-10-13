package com.smartelevate.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Cuenta de una persona. Se crea siempre como usuario común y sin verificar: el constructor no recibe el rol,
 * así el registro no puede otorgar permisos ni por error (ADR 0009). Cambiar el rol o verificar el email
 * llegan con sus propias tareas (#121, #119).
 */
@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name")
    private String fullName;

    /** Siempre en minúsculas. */
    private String email;

    private String legajo;

    /** Hash BCrypt de la contraseña. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "declared_user_type")
    private DeclaredUserType declaredUserType;

    @Column(name = "email_verified")
    private boolean emailVerified;

    @Column(name = "created_at")
    private Instant createdAt;

    public User(String fullName, String email, String legajo, String passwordHash,
                DeclaredUserType declaredUserType, Instant createdAt) {
        this.fullName = fullName;
        this.email = email;
        this.legajo = legajo;
        this.passwordHash = passwordHash;
        this.declaredUserType = declaredUserType;
        this.createdAt = createdAt;
        this.role = Role.USER;
        this.emailVerified = false;
    }
}
