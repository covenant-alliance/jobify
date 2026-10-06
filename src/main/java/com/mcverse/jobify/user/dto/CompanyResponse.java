package com.mcverse.jobify.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Company linked to an employer profile")
public record CompanyResponse(
        @Schema(description = "UUID company identifier", example = "7f3b2c4a-1234-5678-abcd-ef0123456789")
        String id,

        @Schema(description = "Company name", example = "Acme Corporation")
        String name,

        @Schema(description = "Logo URL relative to the API address (public, cacheable); null when none",
                example = "/companies/7f3b.../logo?v=1760000000000", nullable = true)
        String logoUrl
) {}
