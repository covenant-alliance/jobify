package com.mcverse.jobify.match;

import com.mcverse.jobify.common.response.PagedResponse;
import com.mcverse.jobify.match.dto.CandidateResponse;
import com.mcverse.jobify.match.dto.JobMatchResponse;
import com.mcverse.jobify.match.dto.RecommendedJobResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Matching", description = "How well jobs fit a seeker — seekers only, requires Bearer token")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @Operation(summary = "How well does this job fit me?",
            description = "SEEKER only. Scores the signed-in seeker's skills and saved preferences against one open "
                    + "job, with the reasons. Answers 503 with a readable message while the matching service is "
                    + "unavailable; nothing else in the API is affected.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The score and its explanation"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Caller is not a seeker"),
            @ApiResponse(responseCode = "404", description = "Unknown or closed job"),
            @ApiResponse(responseCode = "503", description = "The matching service is unavailable"),
    })
    @GetMapping("/jobs/{id}/match")
    public JobMatchResponse match(@AuthenticationPrincipal UserDetails principal,
                                  @Parameter(description = "Job post ID", example = "3") @PathVariable Integer id) {
        return matchService.matchFor(principal.getUsername(), id);
    }

    @Operation(summary = "Jobs recommended for me, best fit first",
            description = "SEEKER only. Open jobs the seeker has not applied to, that are remote or in one of the "
                    + "seeker's preferred countries (all countries when none are saved), newest 200 first narrowed, then "
                    + "ranked by how well they fit. Answers 503 with a readable message while the matching service is "
                    + "unavailable; there is deliberately no fallback list that would be shown as 'recommended'.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page, best score first (possibly empty)"),
            @ApiResponse(responseCode = "400", description = "page or size out of range"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Caller is not a seeker"),
            @ApiResponse(responseCode = "503", description = "The matching service is unavailable"),
    })
    @GetMapping("/jobs/recommended")
    public PagedResponse<RecommendedJobResponse> recommended(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Page number, starting at 0", example = "0") @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size, 1 to 50 (default 20)", example = "20") @RequestParam(required = false) Integer size) {
        return matchService.recommend(principal.getUsername(), page, size);
    }

    @Operation(summary = "Applicants of my job, best fit first",
            description = "EMPLOYER only: the employer who posted the job, or anyone in the same company. Ranks the "
                    + "seekers who applied (not withdrawn); it is not a search across all seekers. Only skills and "
                    + "saved preferences are sent to the matching service, never names or CV text. Answers 503 with a "
                    + "readable message while the matching service is unavailable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page, best score first (possibly empty)"),
            @ApiResponse(responseCode = "400", description = "page or size out of range"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Not an employer of the company that posted the job"),
            @ApiResponse(responseCode = "404", description = "Job not found"),
            @ApiResponse(responseCode = "503", description = "The matching service is unavailable"),
    })
    @GetMapping("/jobs/{id}/candidates")
    public PagedResponse<CandidateResponse> candidates(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Job post ID", example = "3") @PathVariable Integer id,
            @Parameter(description = "Page number, starting at 0", example = "0") @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size, 1 to 50 (default 20)", example = "20") @RequestParam(required = false) Integer size) {
        return matchService.candidatesFor(principal.getUsername(), id, page, size);
    }
}
