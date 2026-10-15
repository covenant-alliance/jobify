package com.mcverse.jobify.auth.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One refresh token, stored only as a SHA-256 hash (the token itself is shown once, to its owner). Tokens form a chain:
 * each use marks the token used and issues the next one in the same {@code familyId} (one family per sign-in). Using an
 * already-used token again, other than a moment later, means a copy was stolen, so the whole family is revoked.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_tokens_username", columnList = "username"),
        @Index(name = "idx_refresh_tokens_family", columnList = "familyId"),
        @Index(name = "idx_refresh_tokens_expires", columnList = "expiresAt")})
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String familyId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    private LocalDateTime revokedAt;

    protected RefreshToken() {}

    public RefreshToken(String tokenHash, String username, String familyId, LocalDateTime createdAt,
                        LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.username = username;
        this.familyId = familyId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getUsername()          { return username; }
    public String getFamilyId()          { return familyId; }
    public LocalDateTime getExpiresAt()  { return expiresAt; }
    public LocalDateTime getUsedAt()     { return usedAt; }
    public LocalDateTime getRevokedAt()  { return revokedAt; }

    public void markUsed(LocalDateTime now) { this.usedAt = now; }
}
