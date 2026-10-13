package com.smartelevate.user.repository;

import com.smartelevate.user.model.EmailToken;
import com.smartelevate.user.model.TokenPurpose;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {

    Optional<EmailToken> findByTokenHash(String tokenHash);

    Optional<EmailToken> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(UUID userId, TokenPurpose purpose);

    @Modifying
    void deleteByUserIdAndPurposeAndUsedAtIsNull(UUID userId, TokenPurpose purpose);

    /** Marca como usado de forma atómica: si dos pedidos llegan juntos, solo uno ve {@code 1}. */
    @Modifying(clearAutomatically = true)
    @Query("update EmailToken t set t.usedAt = :now "
            + "where t.tokenHash = :hash and t.purpose = :purpose and t.usedAt is null and t.expiresAt > :now")
    int markUsed(@Param("hash") String hash, @Param("purpose") TokenPurpose purpose, @Param("now") Instant now);
}
