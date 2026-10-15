package com.mcverse.jobify.admin;

import com.mcverse.jobify.admin.dto.AccountStateResponse;
import com.mcverse.jobify.admin.dto.JobModerationRequest;
import com.mcverse.jobify.admin.dto.JobModerationResponse;
import com.mcverse.jobify.admin.dto.ModerationRequest;
import com.mcverse.jobify.admin.service.AdminModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@Tag(name = "Admin", description = "Admin-only endpoints — require ADMIN role")
@SecurityRequirement(name = "bearerAuth")
public class AdminModerationController {

    private final AdminModerationService moderation;

    public AdminModerationController(AdminModerationService moderation) {
        this.moderation = moderation;
    }

    @Operation(summary = "Disable an account",
            description = "ADMIN only. The person can no longer sign in and any token they hold stops working (401). "
                    + "Not for administrators or yourself (422). Nothing of theirs is deleted.")
    @PostMapping("/users/{username}/disable")
    public AccountStateResponse disable(@AuthenticationPrincipal UserDetails principal, @PathVariable String username,
                                        @Valid @RequestBody(required = false) ModerationRequest request) {
        boolean enabled = moderation.setEnabled(principal.getUsername(), username, false,
                request == null ? null : request.reason());
        return new AccountStateResponse(username, enabled);
    }

    @Operation(summary = "Enable an account again", description = "ADMIN only.")
    @PostMapping("/users/{username}/enable")
    public AccountStateResponse enable(@AuthenticationPrincipal UserDetails principal, @PathVariable String username,
                                       @Valid @RequestBody(required = false) ModerationRequest request) {
        boolean enabled = moderation.setEnabled(principal.getUsername(), username, true,
                request == null ? null : request.reason());
        return new AccountStateResponse(username, enabled);
    }

    @Operation(summary = "Delete an account directly",
            description = "ADMIN only, for cases that cannot wait for a deletion request. Same effect as approving one "
                    + "(a company's other people keep its jobs). Not for administrators or yourself (422). "
                    + "`reason` is required (400 `reason is required.`) and goes to the audit log.")
    @DeleteMapping("/users/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@AuthenticationPrincipal UserDetails principal, @PathVariable String username,
                           @Parameter(description = "Why, up to 500 characters") @RequestParam(required = false) String reason) {
        moderation.deleteAccount(principal.getUsername(), username, reason);
    }

    @Operation(summary = "Close or reopen any job",
            description = "ADMIN only. Closing needs a `reason`, which the job's owners (everyone in the company) receive "
                    + "as a notification of type SYSTEM. 422 if the job is already in that state.")
    @PatchMapping("/jobs/{id}/available")
    public JobModerationResponse setJobAvailable(@AuthenticationPrincipal UserDetails principal, @PathVariable Integer id,
                                                 @Valid @RequestBody JobModerationRequest request) {
        boolean available = moderation.setJobAvailable(principal.getUsername(), id, request.available(), request.reason());
        return new JobModerationResponse(id, available);
    }

    @Operation(summary = "Delete a job",
            description = "ADMIN only. Removes the job with its applications, saved copies and pictures; the owners are "
                    + "notified with the `reason` (required, 400 otherwise).")
    @DeleteMapping("/jobs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJob(@AuthenticationPrincipal UserDetails principal, @PathVariable Integer id,
                          @Parameter(description = "Why, up to 500 characters") @RequestParam(required = false) String reason) {
        moderation.deleteJob(principal.getUsername(), id, reason);
    }
}
