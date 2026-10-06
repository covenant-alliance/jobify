package com.mcverse.jobify.config;

import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.auth.model.Role;
import com.mcverse.jobify.auth.repository.AuthUserRepository;
import com.mcverse.jobify.auth.security.PasswordPolicy;
import com.mcverse.jobify.auth.security.UserDetailsServiceImpl;
import com.mcverse.jobify.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator on a database that has none, from {@code app.bootstrap.admin.username} and
 * {@code app.bootstrap.admin.password} (environment: {@code BOOTSTRAP_ADMIN_USERNAME} / {@code BOOTSTRAP_ADMIN_PASSWORD}).
 * This is how a production database, which is not seeded with demo accounts, gets its first admin.
 * <ul>
 *   <li>Does nothing when the settings are empty, or when an admin already exists, so it is safe to leave
 *       configured and cannot reset or add admins after the first one.</li>
 *   <li>The password must satisfy {@link PasswordPolicy}; if it does not, startup fails with a clear message
 *       instead of creating a weak admin.</li>
 *   <li>The password is never logged.</li>
 * </ul>
 */
@Component
@Order(10)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AuthUserRepository users;
    private final UserDetailsServiceImpl userDetailsService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditLog auditLog;
    private final String username;
    private final String password;

    public AdminBootstrap(AuthUserRepository users, UserDetailsServiceImpl userDetailsService,
                          UserService userService, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                          AuditLog auditLog,
                          @Value("${app.bootstrap.admin.username:}") String username,
                          @Value("${app.bootstrap.admin.password:}") String password) {
        this.users = users;
        this.userDetailsService = userDetailsService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.auditLog = auditLog;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isEmpty() && password.isEmpty()) return;
        if (username.isEmpty() || password.isEmpty()) {
            throw new IllegalStateException(
                    "Set both BOOTSTRAP_ADMIN_USERNAME and BOOTSTRAP_ADMIN_PASSWORD, or neither.");
        }
        if (users.countByRole(Role.ADMIN) > 0) {
            log.info("Bootstrap admin skipped: an administrator already exists");
            return;
        }
        if (users.existsByUsername(username)) {
            throw new IllegalStateException("Cannot bootstrap the admin: the username '" + username
                    + "' is already used by a non-admin account. Choose another BOOTSTRAP_ADMIN_USERNAME.");
        }
        try {
            passwordPolicy.validate(username, password);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD is not acceptable: " + e.getMessage(), e);
        }
        userDetailsService.save(username, passwordEncoder.encode(password), Role.ADMIN);
        userService.createProfile(username, "Admin", "User", Role.ADMIN);
        auditLog.record("system", "BOOTSTRAP_ADMIN_CREATED", "username=" + username);
    }
}
