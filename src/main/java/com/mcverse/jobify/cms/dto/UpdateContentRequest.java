package com.mcverse.jobify.cms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Bulk content update — content key to new value")
public record UpdateContentRequest(
        @NotNull(message = "is required")
        @Size(max = 500, message = "must have at most 500 entries")
        Map<@NotBlank(message = "keys must not be blank") String,
                @NotNull(message = "values must not be null") @Size(max = 4000, message = "values must be at most 4000 characters") String> values
) {}
