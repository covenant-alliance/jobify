package com.mcverse.jobify.auth.controller;

import com.mcverse.jobify.auth.dto.LoginRequest;
import com.mcverse.jobify.auth.dto.RefreshRequest;
import com.mcverse.jobify.auth.dto.RegisterRequest;
import com.mcverse.jobify.auth.dto.TokenResponse;
import com.mcverse.jobify.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Public endpoints — no Authorization header required")
@RestController
@RequestMapping("/auth")
@SecurityRequirements
public class AuthController {

    @Autowired
    private AuthService authService;

    @Operation(
            summary = "Register a new account",
            description = "Creates an AppUser credential record and a matching domain profile (Seeker or Employer) in a single transaction. Returns a JWT immediately — the user is logged in right after registration."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created successfully",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or username already taken",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
    })
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @Operation(
            summary = "Authenticate an existing account",
            description = "Validates credentials and returns a signed JWT. Pass the returned token as 'Authorization: Bearer <token>' on all protected endpoints."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid username or password",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
    })
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(
            summary = "Trade a refresh token for a new access token",
            description = "Send the refresh token from login, register or the last refresh. Returns a new access token and a "
                    + "NEW refresh token; the one you sent stops working (store the new one). A token used a second time "
                    + "(other than within seconds, as with two tabs) is treated as stolen and ends that whole sign-in. "
                    + "Call it shortly before the access token expires, or after a 401 on a normal route."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New tokens",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "refreshToken missing"),
            @ApiResponse(responseCode = "401", description = "Unknown, expired, revoked or reused refresh token, or the "
                    + "account is deleted or disabled: sign in again"),
            @ApiResponse(responseCode = "429", description = "Too many requests"),
    })
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @Operation(
            summary = "Sign out: end the sign-in a refresh token belongs to",
            description = "Always 204, whether or not the token was known. The access token stays valid until it expires "
                    + "(its lifetime is short)."
    )
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
    }

    @Operation(
            summary = "Sign out everywhere",
            description = "Needs a valid access token. Ends every sign-in (every refresh token) of the caller, on every device.",
            security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@org.springframework.security.core.annotation.AuthenticationPrincipal
                          org.springframework.security.core.userdetails.UserDetails principal) {
        authService.logoutEverywhere(principal.getUsername());
    }
}
