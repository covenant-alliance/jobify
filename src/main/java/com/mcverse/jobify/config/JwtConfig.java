package com.mcverse.jobify.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

    /** Placeholder key that lives in application-dev.properties. It is public, so it must never sign real tokens. */
    static final String DEV_PLACEHOLDER_SECRET =
            "am9iaWZ5LXNlY3VyaXR5LWp3dC1rZXktcGxlYXNlLWNoYW5nZS1tZQ==";
    private static final int MIN_KEY_BYTES = 32;

    private final Environment environment;

    private String secret;
    private long expiration = 86400000L;
    /** How long a refresh token stays valid without being used (it is replaced on every use). */
    private int refreshExpirationDays = 30;
    /** A refresh token presented again this soon after its first use is a double click or a second tab, not theft. */
    private int refreshGraceSeconds = 10;

    public JwtConfig(Environment environment) {
        this.environment = environment;
    }

    public String getSecret()             { return secret; }
    public long getExpiration()           { return expiration; }
    public int getRefreshExpirationDays() { return refreshExpirationDays; }
    public int getRefreshGraceSeconds()   { return refreshGraceSeconds; }

    public void setSecret(String secret)           { this.secret = secret; }
    public void setExpiration(long expiration)     { this.expiration = expiration; }
    public void setRefreshExpirationDays(int days) { this.refreshExpirationDays = days; }
    public void setRefreshGraceSeconds(int seconds) { this.refreshGraceSeconds = seconds; }

    /** Fails startup on a missing, malformed, too short or (outside the dev profile) placeholder secret. */
    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret is not set. Provide a Base64 encoded key of at least 256 bits in the JWT_SECRET "
                            + "environment variable (for example: openssl rand -base64 32).");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("jwt.secret must be Base64 encoded.", e);
        }
        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("jwt.secret is too short: HS256 needs at least 256 bits (32 bytes).");
        }
        if (DEV_PLACEHOLDER_SECRET.equals(secret) && !environment.acceptsProfiles(Profiles.of("dev"))) {
            throw new IllegalStateException(
                    "jwt.secret is the public development placeholder. Set JWT_SECRET to a private key "
                            + "or run with the 'dev' profile.");
        }
    }
}
