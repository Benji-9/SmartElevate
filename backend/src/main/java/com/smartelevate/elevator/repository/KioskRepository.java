package com.smartelevate.elevator.repository;

import com.smartelevate.elevator.model.Kiosk;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KioskRepository extends JpaRepository<Kiosk, UUID> {

    Optional<Kiosk> findBySecretHash(String secretHash);

    List<Kiosk> findAllByOrderByCreatedAtAsc();
}
