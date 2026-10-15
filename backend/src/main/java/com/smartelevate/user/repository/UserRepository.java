package com.smartelevate.user.repository;

import com.smartelevate.user.model.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    java.util.Optional<User> findByEmail(String email);

    boolean existsByLegajo(String legajo);

    /** Bloquea la fila del usuario hasta el fin de la transacción: serializa sus reservas simultáneas. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> lockById(@org.springframework.data.repository.query.Param("id") UUID id);

    /** Docentes declarados con email verificado y sin ninguna decisión de prioridad de docente (cola del ADMIN). */
    @Query("""
            select u from User u
            where u.declaredUserType = com.smartelevate.user.model.DeclaredUserType.TEACHER
              and u.emailVerified = true
              and not exists (select 1 from UserPriority p where p.userId = u.id
                              and p.category = com.smartelevate.priority.model.PriorityCategory.TEACHER)
            order by u.createdAt""")
    List<User> findPendingTeachers();
}
