package com.mcverse.jobify.company.controller;

import com.mcverse.jobify.application.dto.EmployerStatsResponse;
import com.mcverse.jobify.company.dto.ActivityResponse;
import com.mcverse.jobify.company.dto.ChangeRoleRequest;
import com.mcverse.jobify.company.dto.InvitationResponse;
import com.mcverse.jobify.company.dto.InviteRequest;
import com.mcverse.jobify.company.dto.MemberResponse;
import com.mcverse.jobify.company.service.CompanyDashboardService;
import com.mcverse.jobify.company.service.CompanyTeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Company team", description = "Invitations, members, roles, the company dashboard and its activity feed "
        + "— employers only, requires Bearer token")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class CompanyTeamController {

    private final CompanyTeamService team;
    private final CompanyDashboardService dashboard;

    public CompanyTeamController(CompanyTeamService team, CompanyDashboardService dashboard) {
        this.team = team;
        this.dashboard = dashboard;
    }

    // ------------------------------------------------------------------------------------- invitations

    @Operation(summary = "Invite an employer account to the company",
            description = "Company owner only. The invitee sees it in GET /invitations/me and is notified; it is valid "
                    + "for 14 days. 422 for an unknown or non-employer username, someone already in a company, "
                    + "yourself, or a second pending invitation to the same person.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Invitation sent"),
            @ApiResponse(responseCode = "403", description = "Not an owner of this company"),
            @ApiResponse(responseCode = "404", description = "Company not found"),
            @ApiResponse(responseCode = "422", description = "Cannot invite that person")})
    @PostMapping("/companies/{id}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(@AuthenticationPrincipal UserDetails principal,
                                     @Parameter(description = "Company UUID") @PathVariable String id,
                                     @Valid @RequestBody InviteRequest request) {
        return team.invite(principal.getUsername(), id, request.username());
    }

    @Operation(summary = "List the company's pending invitations", description = "Company owner only, newest first.")
    @GetMapping("/companies/{id}/invitations")
    public List<InvitationResponse> pending(@AuthenticationPrincipal UserDetails principal, @PathVariable String id) {
        return team.pendingFor(principal.getUsername(), id);
    }

    @Operation(summary = "Cancel a pending invitation", description = "Company owner only. 422 if it was already answered.")
    @DeleteMapping("/companies/{id}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal UserDetails principal, @PathVariable String id,
                       @PathVariable String invitationId) {
        team.cancel(principal.getUsername(), id, invitationId);
    }

    @Operation(summary = "My invitations", description = "Employers only: pending invitations addressed to me, newest first.")
    @GetMapping("/invitations/me")
    public List<InvitationResponse> mine(@AuthenticationPrincipal UserDetails principal) {
        return team.mine(principal.getUsername());
    }

    @Operation(summary = "Accept an invitation",
            description = "Only the invited person. They join as a MANAGER; their other pending invitations are "
                    + "cancelled. 422 if it was answered or expired, or if they already belong to a company.")
    @PostMapping("/invitations/{id}/accept")
    public InvitationResponse accept(@AuthenticationPrincipal UserDetails principal, @PathVariable String id) {
        return team.accept(principal.getUsername(), id);
    }

    @Operation(summary = "Decline an invitation", description = "Only the invited person.")
    @PostMapping("/invitations/{id}/decline")
    public InvitationResponse decline(@AuthenticationPrincipal UserDetails principal, @PathVariable String id) {
        return team.decline(principal.getUsername(), id);
    }

    // --------------------------------------------------------------------------------------------- members

    @Operation(summary = "List the company's members", description = "Any member (owner or manager), oldest first.")
    @GetMapping("/companies/{id}/members")
    public List<MemberResponse> members(@AuthenticationPrincipal UserDetails principal, @PathVariable String id) {
        return team.members(principal.getUsername(), id);
    }

    @Operation(summary = "Remove a member", description = "Company owner only. Their jobs stay with the company "
            + "(they pass to the owner who removes them). To leave yourself use DELETE /companies/me/membership.")
    @DeleteMapping("/companies/{id}/members/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal UserDetails principal, @PathVariable String id,
                       @PathVariable String username) {
        team.removeMember(principal.getUsername(), id, username);
    }

    @Operation(summary = "Change a member's role", description = "Company owner only. A company always keeps at "
            + "least one owner (422 otherwise).")
    @PutMapping("/companies/{id}/members/{username}/role")
    public MemberResponse changeRole(@AuthenticationPrincipal UserDetails principal, @PathVariable String id,
                                     @PathVariable String username, @Valid @RequestBody ChangeRoleRequest request) {
        return team.changeRole(principal.getUsername(), id, username, request.role());
    }

    @Operation(summary = "Leave my company", description = "The jobs I posted stay with the company. 422 for the only "
            + "owner (make a colleague an owner first) and for a person alone in their company.")
    @DeleteMapping("/companies/me/membership")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal UserDetails principal) {
        team.leave(principal.getUsername());
    }

    // ----------------------------------------------------------------------------- dashboard and activity

    @Operation(summary = "Company dashboard numbers",
            description = "Any member. The same shape as GET /employers/me/stats, across every job of the company.")
    @GetMapping("/companies/{id}/stats")
    public EmployerStatsResponse stats(@AuthenticationPrincipal UserDetails principal, @PathVariable String id) {
        return dashboard.stats(principal.getUsername(), id);
    }

    @Operation(summary = "What the team did", description = "Any member. Newest first: jobs posted, edited, closed, "
            + "applications moved, people invited, joined, left, removed, roles changed. Plain text.")
    @GetMapping("/companies/{id}/activity")
    public List<ActivityResponse> activity(@AuthenticationPrincipal UserDetails principal, @PathVariable String id,
                                           @Parameter(description = "How many, 1 to 100 (default 50)")
                                           @RequestParam(required = false) Integer limit) {
        return dashboard.activity(principal.getUsername(), id, limit);
    }
}
