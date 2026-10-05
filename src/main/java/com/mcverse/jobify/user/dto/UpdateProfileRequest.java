package com.mcverse.jobify.user.dto;

import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body to update a user profile's name fields")
public record UpdateProfileRequest(
        @Schema(description = "Updated first name", example = "Jane")
        @NotBlank(message = "is required") @Size(max = 100, message = "must be at most 100 characters") String name,

        @Schema(description = "Updated last name", example = "Smith")
        @NotBlank(message = "is required") @Size(max = 100, message = "must be at most 100 characters") String lastName
) {}
