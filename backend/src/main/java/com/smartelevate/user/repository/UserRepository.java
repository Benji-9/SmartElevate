package com.smartelevate.user.repository;

import com.smartelevate.user.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    java.util.Optional<User> findByEmail(String email);

    boolean existsByLegajo(String legajo);

    /** Bloquea la fila del usuario hasta el fin de la transacción: serializa sus reservas simultáneas. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> lockById(@org.springframework.data.repository.query.Param("id") UUID id);
}
