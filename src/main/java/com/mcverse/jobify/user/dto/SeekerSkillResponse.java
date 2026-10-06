package com.mcverse.jobify.user.dto;

import com.mcverse.jobify.user.model.ProficiencyLevel;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A skill on a seeker's profile")
public record SeekerSkillResponse(
        @Schema(description = "Seeker-skill entry UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        String id,

        @Schema(description = "Skill name", example = "Java")
        String skillName,

        @Schema(description = "Skill category, optional", example = "Programming Language", nullable = true)
        String category,

        @Schema(description = "Self-reported proficiency level", example = "ADVANCED")
        ProficiencyLevel proficiencyLevel,

        @Schema(description = "Years of experience with this skill, optional", example = "4.5", nullable = true)
        Double yearsOfExperience
) {}
