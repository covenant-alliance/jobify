package com.mcverse.jobify.application.dto;

import com.mcverse.jobify.application.model.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "An application as seen by the employer who owns the job, with a summary of the applicant")
public record JobApplicationResponse(
        @Schema(description = "Application id (UUID)") String id,
        @Schema(description = "Where the application stands") ApplicationStatus status,
        @Schema(description = "Cover note, if any", nullable = true) String coverNote,
        @Schema(description = "When the application was submitted") LocalDateTime createdAt,
        @Schema(description = "When the status last changed") LocalDateTime updatedAt,
        Applicant applicant
) {
    @Schema(description = "The seeker who applied")
    public record Applicant(
            @Schema(description = "Seeker profile id (UUID)") String seekerId,
            String username,
            String name,
            String lastName,
            @Schema(description = "Whether the seeker has a CV on file") boolean hasCv
    ) {}
}
