package com.mcverse.jobify.match.dto;

import com.mcverse.jobify.job.dto.JobPostResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "A job recommended to the signed-in seeker, with how well it fits")
public record RecommendedJobResponse(
        @Schema(description = "The job, in the same shape as GET /jobs") JobPostResponse job,
        @Schema(description = "0 to 100, best first in the list", example = "76") int score,
        @Schema(description = "Names of the required skills the seeker has") List<String> matchedSkills,
        @Schema(description = "Names of the required skills the seeker does not have") List<String> missingSkills) {}
