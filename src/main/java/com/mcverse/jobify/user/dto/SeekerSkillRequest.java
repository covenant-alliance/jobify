package com.mcverse.jobify.user.dto;

import com.mcverse.jobify.user.model.ProficiencyLevel;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body to add or update a skill on a seeker's profile")
public record SeekerSkillRequest(
        @Schema(description = "Skill name — matched case-insensitively against the skill catalog, " +
                "creating a new catalog entry if none exists", example = "Java")
        @NotBlank(message = "is required") @Size(max = 100, message = "must be at most 100 characters") String skillName,

        @Schema(description = "Skill category, optional — only used when the skill is newly created",
                example = "Programming Language", nullable = true)
        @Size(max = 100, message = "must be at most 100 characters")
        String category,

        @Schema(description = "Self-reported proficiency level", example = "ADVANCED")
        @NotNull(message = "is required") ProficiencyLevel proficiencyLevel,

        @Schema(description = "Years of experience with this skill, optional", example = "4.5", nullable = true)
        @DecimalMin(value = "0.0", message = "must be between 0 and 80")
        @DecimalMax(value = "80.0", message = "must be between 0 and 80")
        Double yearsOfExperience
) {}
