package com.mcverse.jobify.match;

import com.mcverse.jobify.match.dto.JobMatchResponse;
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
}
