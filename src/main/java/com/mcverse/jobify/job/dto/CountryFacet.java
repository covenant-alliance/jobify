package com.mcverse.jobify.job.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A country jobs can be filtered by, with how many jobs are there")
public record CountryFacet(
        @Schema(description = "ISO 3166-1 alpha-2 code; pass it as `countryCode` to GET /jobs/search", example = "DE")
        String countryCode,

        @Schema(description = "English country name", example = "Germany") String country,

        @Schema(description = "Number of jobs counted (open ones by default)", example = "12") long jobs
) {}
