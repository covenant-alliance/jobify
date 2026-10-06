package com.mcverse.jobify.application.controller;

import com.mcverse.jobify.application.dto.EmployerStatsResponse;
import com.mcverse.jobify.application.service.EmployerStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Employer stats", description = "How the employer's postings perform — employers only, requires Bearer token")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/employers/me")
public class EmployerStatsController {

    private final EmployerStatsService employerStatsService;

    public EmployerStatsController(EmployerStatsService employerStatsService) {
        this.employerStatsService = employerStatsService;
    }

    @Operation(summary = "My job statistics",
            description = "EMPLOYER only, own jobs only. Jobs open and closed, applications by current status, " +
                    "applications received per week for the last 12 weeks, and the same counts for each job. " +
                    "Job views are not tracked, so they are not included.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The employer's statistics",
                    content = @Content(schema = @Schema(implementation = EmployerStatsResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an employer"),
    })
    @GetMapping("/stats")
    public EmployerStatsResponse stats(@AuthenticationPrincipal UserDetails principal) {
        return employerStatsService.statsFor(principal.getUsername());
    }
}
