package nz.ac.aut.comp713.login_service.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import nz.ac.aut.comp713.login_service.model.LoginSession;

public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {
    @EntityGraph(attributePaths = "account")
    Optional<LoginSession> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    @Modifying
    @Query("update LoginSession session set session.revokedAt = :now where session.account.id = :accountId and session.revokedAt is null")
    int revokeAllForAccount(@Param("accountId") Long accountId, @Param("now") Instant now);

    long deleteByExpiresAtBefore(Instant instant);
}
