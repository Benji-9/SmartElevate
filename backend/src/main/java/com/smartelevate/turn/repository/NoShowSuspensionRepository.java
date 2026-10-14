package com.smartelevate.turn.repository;

import com.smartelevate.turn.model.NoShowSuspension;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoShowSuspensionRepository extends JpaRepository<NoShowSuspension, Long> {

    /** La última suspensión del usuario: desde su fin arranca de cero el conteo de faltas. */
    Optional<NoShowSuspension> findFirstByUserIdOrderByEndsAtDesc(UUID userId);
}
