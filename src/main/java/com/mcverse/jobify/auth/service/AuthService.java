package com.mcverse.jobify.auth.service;

import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.auth.dto.LoginRequest;
import com.mcverse.jobify.auth.dto.RegisterRequest;
import com.mcverse.jobify.auth.dto.RefreshRequest;
import com.mcverse.jobify.auth.dto.TokenResponse;
import com.mcverse.jobify.common.exception.InvalidRefreshTokenException;
import com.mcverse.jobify.auth.model.AppUser;
import com.mcverse.jobify.common.exception.LicenseValidationException;
import com.mcverse.jobify.auth.model.Role;
import com.mcverse.jobify.auth.repository.AuthUserRepository;
import com.mcverse.jobify.auth.security.JwtService;
import com.mcverse.jobify.auth.security.UserDetailsServiceImpl;
import com.mcverse.jobify.common.exception.TooManyRequestsException;
import com.mcverse.jobify.config.JwtConfig;
import com.mcverse.jobify.user.service.UserService;
import com.mcverse.jobify.auth.security.LoginAttemptTracker;
import com.mcverse.jobify.auth.security.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);


    @Autowired private UserDetailsServiceImpl userDetailsService;
    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private UserService userService;
    @Autowired private JwtService jwtService;
    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtConfig jwtConfig;
    @Autowired private LoginAttemptTracker loginAttempts;
    @Autowired private PasswordPolicy passwordPolicy;
    @Autowired private AuditLog auditLog;
    @Autowired private RefreshTokenService refreshTokens;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new IllegalArgumentException("Cannot self-register as ADMIN.");
        }
        passwordPolicy.validate(request.username(), request.password());
        userDetailsService.save(request.username(), passwordEncoder.encode(request.password()), request.role());
        log.info("Registered new {} account '{}'", request.role(), request.username());
        auditLog.event(request.username(), "REGISTER", "role=" + request.role());
        userService.createProfile(request.username(), request.firstName(), request.lastName(), request.role());
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        var refresh = refreshTokens.startSession(request.username());
        return new TokenResponse(jwtService.generateToken(userDetails), jwtConfig.getExpiration(),
                request.username(), request.role().name(), refresh.token(), refresh.expiresInMillis());
    }

    public TokenResponse login(LoginRequest request) {
        try {
            loginAttempts.assertNotLocked(request.username());
        } catch (TooManyRequestsException e) {
            auditLog.event(request.username(), "LOGIN_BLOCKED", "locked, retryAfter=" + e.getRetryAfterSeconds() + "s");
            throw e;
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (DisabledException e) {
            // only someone who knows the password is told the account is disabled; anyone else sees a plain failure
            AppUser disabled = authUserRepository.findByUsername(request.username()).orElse(null);
            if (disabled != null && passwordEncoder.matches(request.password(), disabled.getPassword())) {
                auditLog.event(request.username(), "LOGIN_REFUSED_DISABLED", "");
                throw new LicenseValidationException("This account has been disabled. Please contact support.");
            }
            boolean nowLocked = loginAttempts.recordFailure(request.username());
            auditLog.event(request.username(), nowLocked ? "LOGIN_FAILURE_LOCKED" : "LOGIN_FAILURE", "bad credentials");
            throw new BadCredentialsException("Invalid username or password");
        } catch (BadCredentialsException e) {
            boolean nowLocked = loginAttempts.recordFailure(request.username());
            auditLog.event(request.username(), nowLocked ? "LOGIN_FAILURE_LOCKED" : "LOGIN_FAILURE", "bad credentials");
            throw e;
        }
        loginAttempts.recordSuccess(request.username());
        log.info("Login succeeded for '{}'", request.username());
        auditLog.event(request.username(), "LOGIN_SUCCESS", "");
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        AppUser appUser = authUserRepository.findByUsername(request.username()).orElseThrow();
        var refresh = refreshTokens.startSession(request.username());
        return new TokenResponse(jwtService.generateToken(userDetails), jwtConfig.getExpiration(),
                appUser.getUsername(), appUser.getRole().name(), refresh.token(), refresh.expiresInMillis());
    }

    /**
     * New access token and a new refresh token for a valid refresh token. The old refresh token stops working. A deleted
     * or disabled account is refused (and its sign-ins ended).
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponse refresh(RefreshRequest request) {
        var rotation = refreshTokens.rotate(request.refreshToken());
        AppUser user = authUserRepository.findByUsername(rotation.username()).orElse(null);
        if (user == null || !user.isEnabled()) {
            refreshTokens.revokeAll(rotation.username());
            auditLog.event(rotation.username(), "REFRESH_REFUSED", user == null ? "account is gone" : "account is disabled");
            throw new InvalidRefreshTokenException();
        }
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        auditLog.event(user.getUsername(), "TOKEN_REFRESHED", "");
        return new TokenResponse(jwtService.generateToken(userDetails), jwtConfig.getExpiration(), user.getUsername(),
                user.getRole().name(), rotation.next().token(), rotation.next().expiresInMillis());
    }

    /** Ends the sign-in this refresh token belongs to. Always succeeds, so it reveals nothing about the token. */
    public void logout(RefreshRequest request) {
        refreshTokens.revokeSession(request.refreshToken());
    }

    /** Ends every sign-in of the person. */
    public void logoutEverywhere(String username) {
        refreshTokens.revokeAll(username);
        auditLog.event(username, "LOGOUT_EVERYWHERE", "");
    }
}
