package com.mcverse.jobify.account.controller;

import com.mcverse.jobify.account.dto.ChangePasswordRequest;
import com.mcverse.jobify.account.dto.DeletionRequestRequest;
import com.mcverse.jobify.account.dto.DeletionRequestResponse;
import com.mcverse.jobify.account.service.AccountService;
import com.mcverse.jobify.auth.model.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Account", description = "Self-service account settings — requires Bearer token")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/account")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @Operation(summary = "Change the authenticated account's password")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed"),
            @ApiResponse(responseCode = "400", description = "Validation error — new password too short"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "422", description = "Current password is incorrect"),
    })
    @PutMapping("/password")
    public void changePassword(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(principal.getUsername(), request);
    }

    @Operation(summary = "Request account deletion",
            description = "Submits a request for an admin to review. The account is not deleted until an admin approves it.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Request submitted",
                    content = @Content(schema = @Schema(implementation = DeletionRequestResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "422", description = "A pending request already exists"),
    })
    @PostMapping("/deletion-request")
    @ResponseStatus(HttpStatus.CREATED)
    public DeletionRequestResponse requestDeletion(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody(required = false) DeletionRequestRequest request) {
        Role role = Role.valueOf(principal.getAuthorities().iterator().next().getAuthority().replace("ROLE_", ""));
        return accountService.requestDeletion(principal.getUsername(), role,
                request != null ? request : new DeletionRequestRequest(null));
    }

    @Operation(summary = "Get the authenticated account's pending deletion request, if any")
    @GetMapping("/deletion-request")
    public DeletionRequestResponse getMyDeletionRequest(@AuthenticationPrincipal UserDetails principal) {
        return accountService.getMyDeletionRequest(principal.getUsername());
    }

    @Operation(summary = "Cancel the authenticated account's pending deletion request")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Request cancelled"),
            @ApiResponse(responseCode = "404", description = "No pending request found"),
    })
    @DeleteMapping("/deletion-request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelMyDeletionRequest(@AuthenticationPrincipal UserDetails principal) {
        accountService.cancelMyDeletionRequest(principal.getUsername());
    }
}
