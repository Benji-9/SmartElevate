package com.smartelevate.priority.repository;

import com.smartelevate.priority.model.PriorityCategory;
import com.smartelevate.priority.model.PriorityStatus;
import com.smartelevate.priority.model.UserPriority;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPriorityRepository extends JpaRepository<UserPriority, UUID> {

    List<UserPriority> findByUserIdAndStatus(UUID userId, PriorityStatus status);

    Optional<UserPriority> findByUserIdAndCategory(UUID userId, PriorityCategory category);

    List<UserPriority> findByStatus(PriorityStatus status);
}
