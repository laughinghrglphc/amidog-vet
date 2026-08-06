package com.amidog.app.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Query("select token.user.id from EmailVerificationToken token where token.tokenHash = :tokenHash")
    Optional<Long> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("""
            update EmailVerificationToken token
            set token.consumedAt = :consumedAt
            where token.user.id = :userId and token.consumedAt is null
            """)
    int invalidateAllForUser(@Param("userId") Long userId, @Param("consumedAt") Instant consumedAt);

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update EmailVerificationToken token
            set token.consumedAt = :now
            where token.tokenHash = :tokenHash
              and token.consumedAt is null
              and token.expiresAt > :now
            """)
    int consumeIfActiveByTokenHash(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Query("""
            select (count(token) > 0) from EmailVerificationToken token
            where token.tokenHash = :tokenHash
              and token.user.id = :userId
              and token.consumedAt is null
              and token.expiresAt > :now
            """)
    boolean existsActiveForUser(@Param("tokenHash") String tokenHash,
                                @Param("userId") Long userId,
                                @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("""
            delete from EmailVerificationToken token
            where (
                    token.consumedAt is null
                    and token.expiresAt <= :now
               )
               or (
                    token.consumedAt is not null
                    and token.consumedAt <= :consumedCutoff
               )
            """)
    int deleteExpiredOrConsumedBefore(
            @Param("consumedCutoff") Instant consumedCutoff,
            @Param("now") Instant now);
}
