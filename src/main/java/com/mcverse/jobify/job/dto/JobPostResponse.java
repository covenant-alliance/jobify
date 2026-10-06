package com.mcverse.jobify.job.dto;

import com.mcverse.jobify.common.model.EmploymentType;
import com.mcverse.jobify.job.model.RateType;
import com.mcverse.jobify.job.model.WorkMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "A job posting as returned by the API")
public record JobPostResponse(
        @Schema(description = "Unique job post identifier", example = "1")
        Integer postId,

        @Schema(description = "Job title", example = "Senior Java Developer")
        String jobTitle,

        @Schema(description = "Full job description, stored as sanitized rich-text HTML " +
                "(headings, bold, italic, bullet/numbered lists, emoji).",
                example = "<p>We are looking for a <strong>Senior Java Developer</strong>...</p>")
        String jobDescription,

        @Schema(description = "Pay amount, in the unit given by rateType", example = "75.00")
        double rate,

        @Schema(description = "How rate is expressed: HOURLY, MONTHLY, YEARLY or CONTRACT_TOTAL (a fixed total)",
                example = "HOURLY")
        RateType rateType,

        @Schema(description = "Username of the employer who created this post; null if unassigned",
                example = "acme_corp", nullable = true)
        String employerUsername,

        @Schema(description = "Name of the employer's company; null if the employer has not created one",
                example = "TechCorp Ltd", nullable = true)
        String companyName,

        @Schema(description = "Company id (UUID); use GET /companies/{id}", nullable = true)
        String companyId,

        @Schema(description = "Whether the position is still open and accepting applications. " +
                "Use this to hide closed/filled positions in the UI.", example = "true")
        boolean available,

        @Schema(description = "Free-text work location, e.g. city/region", example = "Berlin, DE", nullable = true)
        String location,

        @Schema(description = "Where the work happens", nullable = true)
        WorkMode workMode,

        @Schema(description = "Contract type", nullable = true)
        EmploymentType employmentType,

        @Schema(description = "When this post was created; null for posts that predate this field", nullable = true)
        LocalDateTime createdAt,

        @Schema(description = "Names of skills required for this position, drawn from the shared skill catalog")
        List<String> requiredSkills,

        @Schema(description = "What the employer offers, in display order; empty when none were given")
        List<String> benefits,

        @Schema(description = "What the person will do, in display order; empty when none were given (the front "
                + "end may derive them from the description then)")
        List<String> responsibilities,

        @Schema(description = "What the person needs to bring, in display order; empty when none were given")
        List<String> requirements,

        @Schema(description = "Picture URLs (at most 6), oldest first, relative to the API address. Public; empty "
                + "when none", example = "[\"/jobs/7/images/3f2a...\"]")
        List<String> images,

        @Schema(description = "The company's logo URL, relative to the API address; null when it has none",
                example = "/companies/7f3b.../logo?v=1760000000000", nullable = true)
        String logoUrl,

        @Schema(description = "Id of the place behind `location` (for GET /jobs/locations and the locationId search "
                + "filter); null when the text names no place", nullable = true)
        String locationId
) {}
