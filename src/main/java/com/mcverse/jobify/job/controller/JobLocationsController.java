package com.mcverse.jobify.job.controller;

import com.mcverse.jobify.job.dto.LocationFacet;
import com.mcverse.jobify.job.search.JobSearchCriteria;
import com.mcverse.jobify.job.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Jobs", description = "Job postings")
@RestController
@RequestMapping("/jobs")
public class JobLocationsController {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final LocationService locations;

    public JobLocationsController(LocationService locations) {
        this.locations = locations;
    }

    @Operation(summary = "Places to filter jobs by, with job counts",
            description = "Public. Clean place values learned from the jobs' free-text locations: two spellings of one "
                    + "place (\"Berlin, DE\", \"berlin, Germany\") are one entry, and remote positions are their own "
                    + "entries (\"Remote\", \"Remote, United States\"). Most jobs first. Filter jobs by an entry with "
                    + "`GET /jobs/search?locationId=<id>`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The places (possibly empty)"),
            @ApiResponse(responseCode = "400", description = "Invalid parameter, with a readable message"),
    })
    @SecurityRequirements
    @GetMapping("/locations")
    public List<LocationFacet> locations(
            @Parameter(description = "true (default) = count open jobs, false = closed jobs, all = both")
            @RequestParam(required = false) String available,
            @Parameter(description = "Only places whose name contains this (any case), for a type-ahead. Max 100")
            @RequestParam(required = false) String q,
            @Parameter(description = "How many places, 1 to 500 (default 100)")
            @RequestParam(required = false) Integer limit) {
        var availability = JobSearchCriteria.parseAvailability(available);
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT + ".");
        }
        String contains = q == null || q.isBlank() ? null : q.trim();
        if (contains != null && contains.length() > JobSearchCriteria.MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("q must be at most " + JobSearchCriteria.MAX_TEXT_LENGTH + " characters.");
        }
        Boolean open = switch (availability) {
            case OPEN -> Boolean.TRUE;
            case CLOSED -> Boolean.FALSE;
            case ALL -> null;
        };
        return locations.facets(open, contains, size);
    }
}
