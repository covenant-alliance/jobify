package com.mcverse.jobify.match.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "How well a job fits the signed-in seeker, with the reasons")
public record JobMatchResponse(
        @Schema(description = "The job's postId", example = "3") int jobId,
        @Schema(description = "0 to 100", example = "76") int score,
        @Schema(description = "Required skills the seeker has") List<SkillRef> matchedSkills,
        @Schema(description = "Required skills the seeker does not have") List<SkillRef> missingSkills,
        @Schema(description = "How the score was made up. A part with counted=false could not be judged (the seeker or "
                + "the job gave no data) and is left out of the score, so the score is based on the counted parts only.")
        List<Part> parts) {

    public record SkillRef(String id, String name) {}

    @Schema(description = "One part of the score")
    public record Part(
            @Schema(description = "skills, location, work_mode, employment_type, rate or experience", example = "skills")
            String key,
            @Schema(description = "Weight of this part", example = "60") double weight,
            @Schema(description = "0 to 1; meaningless when counted is false", example = "0.6") double value,
            @Schema(description = "false when it could not be judged and was left out") boolean counted) {}
}
