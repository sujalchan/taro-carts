package nz.ac.aut.comp713.login_service.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import nz.ac.aut.comp713.login_service.model.LoginSession;

/** Database access for active sessions, bulk revocation, and expiry cleanup. */
public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {
    /**
     * Fetches the account with an unrevoked session so validation can check account
     * eligibility.
     */
    @EntityGraph(attributePaths = "account")
    Optional<LoginSession> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    /** Loads any matching session, including revoked sessions, for mobile sign-in-state explanations. */
    @EntityGraph(attributePaths = "account")
    Optional<LoginSession> findByTokenHash(String tokenHash);

    /**
     * Revokes all currently live sessions for an account in one database update.
     */
    @Modifying
    @Query("update LoginSession session set session.revokedAt = :now where session.account.id = :accountId and session.revokedAt is null")
    int revokeAllForAccount(@Param("accountId") Long accountId, @Param("now") Instant now);

    /** Deletes sessions whose expiry instant is earlier than the supplied time. */
    long deleteByExpiresAtBefore(Instant instant);
}
