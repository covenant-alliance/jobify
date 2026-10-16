package com.mcverse.jobify.job.dto;

import com.mcverse.jobify.common.model.EmploymentType;
import com.mcverse.jobify.job.model.ExperienceLevel;
import com.mcverse.jobify.job.model.RateType;
import com.mcverse.jobify.job.model.WorkMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "What a seeker is looking for. Everything is empty or null until the seeker saves something.")
public record SeekerPreferencesResponse(
        String preferredJobTitle,
        @Schema(description = "Upper-case ISO codes, alphabetical") List<String> countryCodes,
        @Schema(description = "In the order REMOTE, HYBRID, ONSITE") List<WorkMode> workModes,
        List<EmploymentType> employmentTypes,
        Double minRate,
        Double maxRate,
        RateType rateType,
        ExperienceLevel experienceLevel,
        @Schema(description = "When it was last saved; null if never") LocalDateTime updatedAt
) {}
