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
