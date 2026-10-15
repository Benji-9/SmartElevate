package com.smartelevate.user.repository;

import com.smartelevate.user.model.LoginFailure;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface LoginFailureRepository extends JpaRepository<LoginFailure, UUID> {

    long countBySubjectAndFailedAtAfter(String subject, Instant since);

    Optional<LoginFailure> findFirstBySubjectAndFailedAtAfterOrderByFailedAtAsc(String subject, Instant since);

    @Transactional
    @Modifying
    @Query("delete from LoginFailure f where f.subject = :subject")
    int deleteBySubject(@Param("subject") String subject);

    /** Poda lo que ya salió de la ventana: la tabla no crece más que los fallos de la última ventana. */
    @Transactional
    @Modifying
    @Query("delete from LoginFailure f where f.failedAt <= :before")
    int deleteOlderThan(@Param("before") Instant before);
}
