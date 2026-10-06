package com.mcverse.jobify.config;

import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.auth.model.Role;
import com.mcverse.jobify.auth.repository.AuthUserRepository;
import com.mcverse.jobify.auth.security.PasswordPolicy;
import com.mcverse.jobify.auth.security.UserDetailsServiceImpl;
import com.mcverse.jobify.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The first admin of a database without demo data comes from environment variables, and only once. */
class AdminBootstrapTest {

    private AuthUserRepository users;
    private UserDetailsServiceImpl userDetails;
    private UserService userService;
    private PasswordEncoder encoder;
    private AuditLog auditLog;

    @BeforeEach
    void setUp() {
        users = mock(AuthUserRepository.class);
        userDetails = mock(UserDetailsServiceImpl.class);
        userService = mock(UserService.class);
        encoder = mock(PasswordEncoder.class);
        auditLog = mock(AuditLog.class);
        when(encoder.encode(anyString())).thenReturn("hashed");
    }

    private AdminBootstrap bootstrap(String username, String password) {
        return new AdminBootstrap(users, userDetails, userService, encoder, new PasswordPolicy(), auditLog, username, password);
    }

    @Test
    void doesNothingWhenNotConfigured() {
        bootstrap("", "").run(null);
        bootstrap(null, null).run(null);
        verifyNoInteractions(users, userDetails, userService);
    }

    @Test
    void createsTheFirstAdminWithAHashedPassword() {
        when(users.countByRole(Role.ADMIN)).thenReturn(0L);
        bootstrap(" root_admin ", "Str0ng-Pass-123").run(null);
        verify(encoder).encode("Str0ng-Pass-123");
        verify(userDetails).save("root_admin", "hashed", Role.ADMIN);
        verify(userService).createProfile("root_admin", "Admin", "User", Role.ADMIN);
        verify(auditLog).record("system", "BOOTSTRAP_ADMIN_CREATED", "username=root_admin");
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        when(users.countByRole(Role.ADMIN)).thenReturn(1L);
        bootstrap("root_admin", "Str0ng-Pass-123").run(null);
        verify(userDetails, never()).save(anyString(), anyString(), any());
    }

    @Test
    void refusesAWeakPasswordAndNamesTheReason() {
        when(users.countByRole(Role.ADMIN)).thenReturn(0L);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> bootstrap("root_admin", "password").run(null));
        assertTrue(e.getMessage().contains("BOOTSTRAP_ADMIN_PASSWORD"));
        assertTrue(e.getMessage().contains("too common"));
        verify(userDetails, never()).save(anyString(), anyString(), any());
    }

    @Test
    void refusesOnlyHalfAConfiguration() {
        assertThrows(IllegalStateException.class, () -> bootstrap("root_admin", "").run(null));
        assertThrows(IllegalStateException.class, () -> bootstrap("", "Str0ng-Pass-123").run(null));
    }

    @Test
    void refusesAUsernameTakenByANonAdmin() {
        when(users.countByRole(Role.ADMIN)).thenReturn(0L);
        when(users.existsByUsername("taken")).thenReturn(true);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> bootstrap("taken", "Str0ng-Pass-123").run(null));
        assertTrue(e.getMessage().contains("already used"));
    }
}
