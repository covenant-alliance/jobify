package com.mcverse.jobify.application.controller;

import com.mcverse.jobify.application.dto.ApplicationResponse;
import com.mcverse.jobify.application.dto.ApplyRequest;
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
}
