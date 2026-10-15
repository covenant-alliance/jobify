package com.mcverse.jobify.auth.service;

import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.auth.model.RefreshToken;
import com.mcverse.jobify.auth.repository.RefreshTokenRepository;
import com.mcverse.jobify.common.exception.InvalidRefreshTokenException;
import com.mcverse.jobify.config.JwtConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Refresh tokens: long-lived, opaque, stored hashed, replaced on every use. A stolen token is caught because the owner
 * and the thief cannot both use the same one: the second use revokes the whole sign-in (see {@link RefreshToken}).
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** A freshly issued token (the only time its plain text exists) and how long it lives. */
    public record Issued(String token, long expiresInMillis) {}

    /** The account a rotation belongs to, with the replacement token. */
    public record Rotation(String username, Issued next) {}

    private final RefreshTokenRepository repository;
    private final JwtConfig jwtConfig;
    private final AuditLog auditLog;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, JwtConfig jwtConfig, AuditLog auditLog, Clock clock) {
        this.repository = repository;
        this.jwtConfig = jwtConfig;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneId.systemDefault()));
    }

    /** Starts a new sign-in (a new family of tokens). */
    @Transactional
    public Issued startSession(String username) {
        return issue(username, UUID.randomUUID().toString());
    }

    /**
     * Trades a refresh token for its replacement. Unknown, expired, revoked and reused tokens are refused; a reuse also
     * revokes the whole family (that refusal is committed: no rollback for this exception).
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        RefreshToken token = repository.findByTokenHash(hash(rawToken)).orElseThrow(InvalidRefreshTokenException::new);
        LocalDateTime now = now();
        if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }
        if (token.getUsedAt() == null) {
            token.markUsed(now);
        } else if (token.getUsedAt().plusSeconds(jwtConfig.getRefreshGraceSeconds()).isBefore(now)) {
            repository.revokeFamily(token.getFamilyId(), now);
            auditLog.event(token.getUsername(), "REFRESH_REUSE_DETECTED",
                    "a used refresh token was presented again; every session of this sign-in was ended");
            log.warn("Refresh token reuse for '{}': family revoked", token.getUsername());
            throw new InvalidRefreshTokenException();
        } // else: used a moment ago (two tabs, a double click): hand out another one in the same family
        Issued next = issue(token.getUsername(), token.getFamilyId());
        return new Rotation(token.getUsername(), next);
    }

    /** Ends the sign-in a refresh token belongs to. An unknown token is ignored (logging out twice is fine). */
    @Transactional
    public void revokeSession(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(t -> repository.revokeFamily(t.getFamilyId(), now()));
    }

    /** Ends every sign-in of a person (password changed, account disabled, "log out everywhere"). */
    @Transactional
    public void revokeAll(String username) {
        repository.revokeAllFor(username, now());
    }

    /** Removes every refresh token of an account that is being deleted. */
    @Transactional
    public void deleteAll(String username) {
        repository.deleteByUsername(username);
    }

    /** Nightly: expired tokens (and their used predecessors) are of no use any more. */
    // @Transactional here too: purge called from this method would bypass its own proxy
    @Scheduled(cron = "${jwt.refresh-purge-cron:0 45 3 * * *}")
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteExpiredBefore(now().minusDays(1));
        if (removed > 0) {
            log.info("Deleted {} expired refresh tokens", removed);
        }
    }

    private Issued issue(String username, String familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime now = now();
        LocalDateTime expires = now.plusDays(jwtConfig.getRefreshExpirationDays());
        repository.save(new RefreshToken(hash(raw), username, familyId, now, expires));
        return new Issued(raw, Duration.between(now, expires).toMillis());
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
