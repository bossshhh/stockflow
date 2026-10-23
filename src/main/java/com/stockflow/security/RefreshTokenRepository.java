package com.stockflow.security;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Locks the row (SELECT ... FOR UPDATE): two simultaneous refreshes with the same token run one after
     * the other, so a token can never be rotated twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    /** Housekeeping: tokens past their expiry can never be used again. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now")
    int deleteExpiredBefore(Instant now);

    /** Revokes every still-active token of one login (logout, or reuse of a stolen token). */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int revokeFamily(UUID familyId, Instant now);
}
