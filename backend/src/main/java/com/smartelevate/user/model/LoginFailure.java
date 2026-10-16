package com.smartelevate.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Un login fallido de una cuenta o de una IP (ver V13). {@code subject} es un hash: no guarda emails ni IPs. */
@Entity
@Table(name = "login_failure")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginFailure {

    @Id
    private UUID id;

    private String subject;

    @Column(name = "failed_at")
    private Instant failedAt;

    public LoginFailure(String subject, Instant failedAt) {
        this.id = UUID.randomUUID();
        this.subject = subject;
        this.failedAt = failedAt;
    }
}
