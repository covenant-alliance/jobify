package com.mcverse.jobify.application.controller;

import com.mcverse.jobify.application.dto.ApplicationResponse;
import com.mcverse.jobify.application.dto.ApplicationStatsResponse;
import com.mcverse.jobify.application.dto.ApplyRequest;
import com.mcverse.jobify.application.dto.ChangeApplicationStatusRequest;
import com.mcverse.jobify.application.dto.JobApplicationResponse;
import com.mcverse.jobify.application.model.ApplicationStatus;
import com.mcverse.jobify.application.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Applications", description = "Apply to jobs and track applications — requires Bearer token")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Operation(summary = "Apply to a job",
            description = "SEEKER only. Starts as APPLIED. Applying again after withdrawing re-opens the application.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Application submitted",
                    content = @Content(schema = @Schema(implementation = ApplicationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed (cover note too long)"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Caller is not a seeker"),
            @ApiResponse(responseCode = "404", description = "Job not found"),
            @ApiResponse(responseCode = "422", description = "Job is closed, or you already applied"),
    })
    @PostMapping("/jobs/{id}/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse apply(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Job post ID", example = "3") @PathVariable Integer id,
            @Valid @RequestBody(required = false) ApplyRequest request) {
        return applicationService.apply(id, request, principal.getUsername());
    }

    @Operation(summary = "List my applications",
            description = "SEEKER only. Newest first, including withdrawn ones. Each item includes a job summary.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's applications (may be empty)",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApplicationResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Caller is not a seeker"),
    })
    @GetMapping("/applications/me")
    public List<ApplicationResponse> myApplications(@AuthenticationPrincipal UserDetails principal) {
        return applicationService.listMine(principal.getUsername());
    }

    @Operation(summary = "My application statistics",
            description = "SEEKER only. Counts for every status (zero-filled), the total, how many are still active " +
                    "(APPLIED, IN_REVIEW, INTERVIEW, OFFER) and the number of applications submitted in each of the " +
                    "last 12 weeks (weeks start on Monday, oldest first, zero-filled).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's statistics",
                    content = @Content(schema = @Schema(implementation = ApplicationStatsResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Caller is not a seeker"),
    })
    @GetMapping("/applications/me/stats")
    public ApplicationStatsResponse myStats(@AuthenticationPrincipal UserDetails principal) {
        return applicationService.statsForSeeker(principal.getUsername());
    }

    @Operation(summary = "Withdraw an application",
            description = "SEEKER, and only the applicant. The application is kept with status WITHDRAWN. " +
                    "Possible from APPLIED, IN_REVIEW, INTERVIEW and OFFER; not from REJECTED or WITHDRAWN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Application withdrawn"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Not the applicant"),
            @ApiResponse(responseCode = "404", description = "Application not found"),
            @ApiResponse(responseCode = "422", description = "Already rejected or withdrawn"),
    })
    @DeleteMapping("/applications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Application id (UUID)") @PathVariable String id) {
        applicationService.withdraw(id, principal.getUsername());
    }

    @Operation(summary = "List the applicants of one of my jobs",
            description = "EMPLOYER, and only the owner of the job. Newest first; withdrawn applications are listed " +
                    "with status WITHDRAWN. Optional `status` filter.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Applications for the job (may be empty)",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobApplicationResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Unknown status value"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Not the owner of the job"),
            @ApiResponse(responseCode = "404", description = "Job not found"),
    })
    @GetMapping("/jobs/{id}/applications")
    public List<JobApplicationResponse> jobApplications(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Job post ID", example = "3") @PathVariable Integer id,
            @Parameter(description = "Only applications in this stage") @RequestParam(required = false)
            ApplicationStatus status) {
        return applicationService.listForJob(id, status, principal.getUsername());
    }

    @Operation(summary = "Move an application to another stage",
            description = "EMPLOYER, and only the owner of the job. Allowed moves: APPLIED to IN_REVIEW or REJECTED; " +
                    "IN_REVIEW to INTERVIEW or REJECTED; INTERVIEW to OFFER or REJECTED; OFFER to REJECTED. " +
                    "REJECTED and WITHDRAWN are final. Employers cannot set APPLIED or WITHDRAWN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated application",
                    content = @Content(schema = @Schema(implementation = JobApplicationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or unknown status"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Not the owner of the job"),
            @ApiResponse(responseCode = "404", description = "Application not found"),
            @ApiResponse(responseCode = "422", description = "That move is not allowed"),
    })
    @PutMapping("/applications/{id}/status")
    public JobApplicationResponse changeStatus(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Application id (UUID)") @PathVariable String id,
            @Valid @RequestBody ChangeApplicationStatusRequest request) {
        return applicationService.changeStatus(id, request.status(), principal.getUsername());
    }
}
