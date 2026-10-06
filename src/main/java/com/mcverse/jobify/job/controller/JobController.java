package com.mcverse.jobify.job.controller;

import com.mcverse.jobify.job.dto.JobPostResponse;
import com.mcverse.jobify.job.dto.UpdateJobAvailabilityRequest;
import com.mcverse.jobify.job.dto.CreateJobRequest;
import com.mcverse.jobify.job.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Jobs", description = "Browse and manage job postings — requires Bearer token")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/jobs")
public class JobController {

    @Autowired
    private JobService jobService;

    @Operation(
            summary = "List job posts",
            description = "Public endpoint — no authentication required. " +
                    "Pass `available=true` to show only open positions (recommended for visitors and seekers). " +
                    "Pass `available=false` to see closed/filled posts. " +
                    "Omit the parameter to return all posts regardless of status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of job posts (may be empty)",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobPostResponse.class)))),
    })
    @SecurityRequirements
    @GetMapping
    public List<JobPostResponse> getAllJobs(
            @Parameter(description = "Filter by availability — true = open only, false = closed only, omit = all",
                    example = "true")
            @RequestParam(required = false) Boolean available) {
        return jobService.getJobs(available);
    }

    @Operation(
            summary = "List my job posts",
            description = "EMPLOYER only. Returns every post owned by the caller, open and closed."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's job posts (may be empty)",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobPostResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an employer"),
    })
    @GetMapping("/mine")
    public List<JobPostResponse> getMyJobs(@AuthenticationPrincipal UserDetails principal) {
        return jobService.getJobsOwnedBy(principal.getUsername());
    }

    @Operation(
            summary = "Get a single job post",
            description = "Public endpoint — no authentication required."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The job post",
                    content = @Content(schema = @Schema(implementation = JobPostResponse.class))),
            @ApiResponse(responseCode = "404", description = "Job post not found"),
    })
    @SecurityRequirements
    @GetMapping("/{id}")
    public JobPostResponse getJob(
            @Parameter(description = "Job post ID", example = "3")
            @PathVariable Integer id) {
        return jobService.getJobById(id);
    }

    @Operation(
            summary = "Create a new job post",
            description = "EMPLOYER only. The description is sanitized to a small HTML allow-list " +
                    "(p, br, ul, ol, li, strong, em, h2, h3, a[href]). New posts are open (available=true)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Job post created",
                    content = @Content(schema = @Schema(implementation = JobPostResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an employer"),
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobPostResponse addJob(@AuthenticationPrincipal UserDetails principal,
                                  @Valid @RequestBody CreateJobRequest request) {
        return jobService.addJob(request, principal.getUsername());
    }

    @Operation(
            summary = "Edit a job post",
            description = "Replaces the editable fields of a post. Only the employer who created it may do this. " +
                    "The open/closed flag is not changed here, use `PATCH /jobs/{id}/available`."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job post updated",
                    content = @Content(schema = @Schema(implementation = JobPostResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Caller does not own this post"),
            @ApiResponse(responseCode = "404", description = "Job post not found"),
    })
    @PutMapping("/{id}")
    public JobPostResponse updateJob(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Job post ID", example = "3") @PathVariable Integer id,
            @Valid @RequestBody CreateJobRequest request) {
        return jobService.updateJob(id, request, principal.getUsername());
    }

    @Operation(
            summary = "Open or close a job posting",
            description = "Sets the `available` flag. Only the employer who created the post may do this."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job post updated",
                    content = @Content(schema = @Schema(implementation = JobPostResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Caller does not own this post"),
            @ApiResponse(responseCode = "404", description = "Job post not found"),
    })
    @PatchMapping("/{id}/available")
    public JobPostResponse updateAvailability(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Job post ID", example = "3")
            @PathVariable Integer id,
            @Valid @RequestBody UpdateJobAvailabilityRequest request) {
        return jobService.updateAvailability(id, request.available(), principal.getUsername());
    }
}
