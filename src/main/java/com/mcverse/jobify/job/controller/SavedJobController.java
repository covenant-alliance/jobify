package com.mcverse.jobify.job.controller;

import com.mcverse.jobify.job.dto.JobPostResponse;
import com.mcverse.jobify.job.service.SavedJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Saved jobs", description = "Bookmark jobs for later — seekers only, requires Bearer token")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class SavedJobController {

    private final SavedJobService savedJobService;

    public SavedJobController(SavedJobService savedJobService) {
        this.savedJobService = savedJobService;
    }

    @Operation(summary = "Save a job", description = "SEEKER only. Idempotent: saving a saved job changes nothing.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The job is saved"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Caller is not a seeker"),
            @ApiResponse(responseCode = "404", description = "Job not found"),
    })
    @PutMapping("/jobs/{id}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@AuthenticationPrincipal UserDetails principal,
                     @Parameter(description = "Job post ID", example = "3") @PathVariable Integer id) {
        savedJobService.save(id, principal.getUsername());
    }

    @Operation(summary = "Remove a saved job", description = "SEEKER only. Idempotent: a job that is not saved is fine.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The job is not saved any more"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Caller is not a seeker"),
            @ApiResponse(responseCode = "404", description = "Job not found"),
    })
    @DeleteMapping("/jobs/{id}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsave(@AuthenticationPrincipal UserDetails principal,
                       @Parameter(description = "Job post ID", example = "3") @PathVariable Integer id) {
        savedJobService.unsave(id, principal.getUsername());
    }

    @Operation(summary = "List my saved jobs",
            description = "SEEKER only. Most recently saved first. Jobs that have since closed stay in the list " +
                    "with `available: false`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's saved jobs (may be empty)",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobPostResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Caller is not a seeker"),
    })
    @GetMapping("/jobs/saved")
    public List<JobPostResponse> saved(@AuthenticationPrincipal UserDetails principal) {
        return savedJobService.listSaved(principal.getUsername());
    }
}
