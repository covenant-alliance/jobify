package com.mcverse.jobify.auth.service;

import com.mcverse.jobify.auth.dto.LoginRequest;
import com.mcverse.jobify.auth.dto.RegisterRequest;
import com.mcverse.jobify.auth.dto.TokenResponse;
import com.mcverse.jobify.auth.model.AppUser;
import com.mcverse.jobify.auth.model.Role;
import com.mcverse.jobify.auth.repository.AuthUserRepository;
import com.mcverse.jobify.auth.security.JwtService;
import com.mcverse.jobify.auth.security.UserDetailsServiceImpl;
import com.mcverse.jobify.config.JwtConfig;
import com.mcverse.jobify.user.service.UserService;
import com.mcverse.jobify.auth.security.LoginAttemptTracker;
import com.mcverse.jobify.auth.security.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
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

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new IllegalArgumentException("Cannot self-register as ADMIN.");
        }
        passwordPolicy.validate(request.username(), request.password());
        userDetailsService.save(request.username(), passwordEncoder.encode(request.password()), request.role());
        log.info("Registered new {} account '{}'", request.role(), request.username());
        userService.createProfile(request.username(), request.firstName(), request.lastName(), request.role());
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        return new TokenResponse(jwtService.generateToken(userDetails), jwtConfig.getExpiration(),
                request.username(), request.role().name());
    }

    public TokenResponse login(LoginRequest request) {
        loginAttempts.assertNotLocked(request.username());
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (BadCredentialsException e) {
            loginAttempts.recordFailure(request.username());
            throw e;
        }
        loginAttempts.recordSuccess(request.username());
        log.info("Login succeeded for '{}'", request.username());
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        AppUser appUser = authUserRepository.findByUsername(request.username()).orElseThrow();
        return new TokenResponse(jwtService.generateToken(userDetails), jwtConfig.getExpiration(),
                appUser.getUsername(), appUser.getRole().name());
    }
}
