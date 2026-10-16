package com.mcverse.jobify.job.dto;

import com.mcverse.jobify.common.model.EmploymentType;
import com.mcverse.jobify.job.model.ExperienceLevel;
import com.mcverse.jobify.job.model.RateType;
import com.mcverse.jobify.job.model.WorkMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "What a seeker is looking for. The whole object replaces the saved one: leave out what you "
        + "do not care about (null, or an empty list).")
public record SeekerPreferencesRequest(
        @Schema(description = "Job title the seeker is after", example = "Backend developer")
        @Size(max = 100, message = "must be at most 100 characters")
        String preferredJobTitle,

        @Schema(description = "ISO 3166-1 alpha-2 country codes, upper or lower case", example = "[\"DE\", \"NL\"]")
        @Size(max = 30, message = "must have at most 30 entries")
        List<String> countryCodes,

        @Schema(description = "REMOTE, HYBRID, ONSITE", example = "[\"REMOTE\", \"HYBRID\"]")
        @Size(max = 3, message = "must have at most 3 entries")
        List<WorkMode> workModes,

        @Schema(description = "FULL_TIME, PART_TIME, CONTRACT, TEMPORARY, INTERNSHIP, FREELANCE, B2B",
                example = "[\"FULL_TIME\"]")
        @Size(max = 10, message = "must have at most 10 entries")
        List<EmploymentType> employmentTypes,

        @Schema(description = "Lowest pay wanted, in the unit of rateType", example = "60000")
        @DecimalMin(value = "0.0", message = "must not be negative")
        @DecimalMax(value = "1000000000", message = "is too large")
        Double minRate,

        @Schema(description = "Highest pay wanted, in the unit of rateType", example = "80000")
        @DecimalMin(value = "0.0", message = "must not be negative")
        @DecimalMax(value = "1000000000", message = "is too large")
        Double maxRate,

        @Schema(description = "Unit of minRate and maxRate: HOURLY, MONTHLY or YEARLY. Required when a rate is given; "
                + "CONTRACT_TOTAL is not allowed.", example = "YEARLY")
        RateType rateType,

        @Schema(description = "INTERN, JUNIOR, MID, SENIOR, LEAD", example = "MID")
        ExperienceLevel experienceLevel
) {}
