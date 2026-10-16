package com.mcverse.matching.grpc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** Startup rules of the token check. The call-time behaviour is covered in MatchingServerTest. */
class TokenAuthInterceptorTest {

    private static MockEnvironment env(String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return env;
    }

    @Test
    void devAcceptsTheLocalPlaceholder() {
        assertDoesNotThrow(() -> new TokenAuthInterceptor(TokenAuthInterceptor.DEV_PLACEHOLDER, env("dev")));
    }

    @Test
    void outsideDevAMissingTokenStopsStartup() {
        assertThrows(IllegalStateException.class, () -> new TokenAuthInterceptor("", env("prod")));
        assertThrows(IllegalStateException.class, () -> new TokenAuthInterceptor(null, env("prod")));
        assertThrows(IllegalStateException.class, () -> new TokenAuthInterceptor("   ", env("prod")));
    }

    @Test
    void outsideDevThePublicPlaceholderIsRefused() {
        assertThrows(IllegalStateException.class,
                () -> new TokenAuthInterceptor(TokenAuthInterceptor.DEV_PLACEHOLDER, env("prod")));
    }

    @Test
    void outsideDevARealTokenIsAccepted() {
        assertDoesNotThrow(() -> new TokenAuthInterceptor("a-long-random-secret", env("prod")));
    }

    @Test
    void evenInDevAnEmptyTokenIsRefused() {
        assertThrows(IllegalStateException.class, () -> new TokenAuthInterceptor("", env("dev")));
    }
}
