package com.mcverse.jobify.job.controller;

import com.mcverse.jobify.job.dto.SeekerPreferencesRequest;
import com.mcverse.jobify.job.dto.SeekerPreferencesResponse;
import com.mcverse.jobify.job.service.SeekerPreferencesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Seeker preferences", description = "What a seeker is looking for — seekers only, requires Bearer token")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/users/seekers/me/preferences")
public class SeekerPreferencesController {

    private final SeekerPreferencesService service;

    public SeekerPreferencesController(SeekerPreferencesService service) {
        this.service = service;
    }

    @Operation(summary = "Get my job preferences",
            description = "SEEKER only. 200 with empty lists and nulls until something has been saved.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The saved preferences"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Caller is not a seeker"),
    })
    @GetMapping
    public SeekerPreferencesResponse get(@AuthenticationPrincipal UserDetails principal) {
        return service.get(principal.getUsername());
    }

    @Operation(summary = "Save my job preferences",
            description = "SEEKER only. Replaces the saved preferences as a whole. A pay range needs a rateType "
                    + "(HOURLY, MONTHLY or YEARLY) and minRate may not exceed maxRate.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The preferences as saved"),
            @ApiResponse(responseCode = "400", description = "Invalid value, with a readable message"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Caller is not a seeker"),
    })
    @PutMapping
    public SeekerPreferencesResponse replace(@AuthenticationPrincipal UserDetails principal,
                                             @Valid @RequestBody SeekerPreferencesRequest request) {
        return service.replace(principal.getUsername(), request);
    }
}
