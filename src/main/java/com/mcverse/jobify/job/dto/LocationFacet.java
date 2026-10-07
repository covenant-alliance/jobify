package com.mcverse.jobify.job.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A place jobs can be filtered by, with how many jobs are there")
public record LocationFacet(
        @Schema(description = "Place id; pass it as `locationId` to GET /jobs/search", example = "3f2a9c1e-...")
        String id,

        @Schema(description = "Ready-to-show name", example = "Berlin, Germany")
        String name,

        @Schema(nullable = true, example = "Berlin") String city,

        @Schema(nullable = true, example = "TX") String region,

        @Schema(description = "ISO 3166-1 alpha-2 code", nullable = true, example = "DE") String countryCode,

        @Schema(description = "English country name", nullable = true, example = "Germany") String country,

        @Schema(description = "True for remote positions (\"Remote\", \"Remote, United States\")") boolean remote,

        @Schema(description = "Number of jobs counted (open ones by default)", example = "12") long jobs
) {}
