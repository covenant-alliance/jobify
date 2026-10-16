package com.mcverse.jobify.match.dto;

import com.mcverse.jobify.application.model.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "An applicant of a job, ranked by how well they fit it")
public record CandidateResponse(
        @Schema(description = "Application id (UUID)") String applicationId,
        @Schema(description = "Where the application stands") ApplicationStatus status,
        @Schema(description = "When the application was submitted") LocalDateTime appliedAt,
        Applicant applicant,
        @Schema(description = "0 to 100, best first in the list", example = "76") int score,
        @Schema(description = "Names of the job's required skills the applicant has") List<String> matchedSkills,
        @Schema(description = "Names of the job's required skills the applicant does not have") List<String> missingSkills) {

    @Schema(description = "The seeker who applied (the same details the employer already sees on the applicants list)")
    public record Applicant(
            @Schema(description = "Seeker profile id (UUID)") String seekerId,
            String username,
            String name,
            String lastName,
            @Schema(description = "Whether the seeker has a CV on file") boolean hasCv) {}
}
