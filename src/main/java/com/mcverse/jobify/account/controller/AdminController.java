package com.mcverse.jobify.account.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import com.mcverse.jobify.account.dto.DeletionRequestResponse;
import com.mcverse.jobify.account.dto.ResolveDeletionRequestRequest;
import com.mcverse.jobify.account.service.AdminAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin", description = "Admin-only endpoints — requires an ADMIN Bearer token")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminAccountService adminAccountService;

    @Operation(summary = "List pending account deletion requests")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pending requests, oldest first",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = DeletionRequestResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Authenticated but not an admin"),
    })
    @GetMapping("/deletion-requests")
    public List<DeletionRequestResponse> listPending() {
        return adminAccountService.listPending();
    }

    @Operation(summary = "Approve a deletion request",
            description = "Permanently deletes the requester's account and profile.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request approved and account deleted"),
            @ApiResponse(responseCode = "404", description = "Request not found"),
            @ApiResponse(responseCode = "422", description = "Request already resolved"),
    })
    @PostMapping("/deletion-requests/{id}/approve")
    public DeletionRequestResponse approve(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Deletion request ID") @PathVariable String id) {
        return adminAccountService.approve(id, principal.getUsername());
    }

    @Operation(summary = "Reject a deletion request")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request rejected"),
            @ApiResponse(responseCode = "404", description = "Request not found"),
            @ApiResponse(responseCode = "422", description = "Request already resolved"),
    })
    @PostMapping("/deletion-requests/{id}/reject")
    public DeletionRequestResponse reject(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Deletion request ID") @PathVariable String id,
            @Valid @RequestBody(required = false) ResolveDeletionRequestRequest body) {
        return adminAccountService.reject(id, body != null ? body : new ResolveDeletionRequestRequest(null),
                principal.getUsername());
    }
}
