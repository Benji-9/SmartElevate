package com.smartelevate.priority.repository;

import com.smartelevate.priority.model.PriorityAudit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriorityAuditRepository extends JpaRepository<PriorityAudit, UUID> {

    List<PriorityAudit> findByTargetUserIdOrderByCreatedAtAsc(UUID targetUserId);
}
