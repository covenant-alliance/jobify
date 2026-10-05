package com.mcverse.jobify.application.dto;

import com.mcverse.jobify.application.model.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "A seeker's application to a job, with a summary of the job")
public record ApplicationResponse(
        @Schema(description = "Application id (UUID)") String id,
        @Schema(description = "Where the application stands") ApplicationStatus status,
        @Schema(description = "Cover note, if any", nullable = true) String coverNote,
        @Schema(description = "When the application was submitted") LocalDateTime createdAt,
        @Schema(description = "When the status last changed") LocalDateTime updatedAt,
        JobSummary job
) {
    @Schema(description = "The job an application belongs to")
    public record JobSummary(
            Integer postId,
            String jobTitle,
            @Schema(nullable = true) String employerUsername,
            @Schema(description = "Company of the employer, if they have one", nullable = true) String companyName,
            @Schema(description = "false once the employer closed the position") boolean available
    ) {}
}
