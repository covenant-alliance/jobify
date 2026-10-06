package com.mcverse.jobify.job.controller;

import com.mcverse.jobify.common.model.EmploymentType;
import com.mcverse.jobify.common.response.PagedResponse;
import com.mcverse.jobify.job.dto.JobPostResponse;
import com.mcverse.jobify.job.model.WorkMode;
import com.mcverse.jobify.job.search.JobSearchCriteria;
import com.mcverse.jobify.job.search.JobSearchService;
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

@Tag(name = "Jobs", description = "Browse and manage job postings")
@RestController
@RequestMapping("/jobs")
public class JobSearchController {

    private final JobSearchService searchService;

    public JobSearchController(JobSearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(
            summary = "Search job posts",
            description = "Public endpoint, no authentication required. Filters, sorts and pages on the server, so the "
                    + "client does not have to download every job. Every filter is optional and they combine with AND. "
                    + "`GET /jobs` is unchanged and still returns the plain array of all jobs. "
                    + "Items are the same `JobPostResponse` as everywhere else. No match is `200` with an empty "
                    + "`content`. Text search is a case-insensitive contains match, not relevance-ranked."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of matching jobs (possibly empty)"),
            @ApiResponse(responseCode = "400", description = "Invalid parameter, with a readable message"),
    })
    @SecurityRequirements
    @GetMapping("/search")
    public PagedResponse<JobPostResponse> search(
            @Parameter(description = "Contains match (any case) on title, company name, location, description words "
                    + "and required-skill names. Max 100 characters.", example = "java")
            @RequestParam(required = false) String q,
            @Parameter(description = "Contains match (any case) on the free-text location", example = "berlin")
            @RequestParam(required = false) String location,
            @Parameter(description = "Place ids from GET /jobs/locations; repeat to allow several. Max 20")
            @RequestParam(required = false) List<String> locationId,
            @Parameter(description = "REMOTE, HYBRID or ONSITE; repeat to allow several")
            @RequestParam(required = false) List<WorkMode> workMode,
            @Parameter(description = "FULL_TIME, PART_TIME, CONTRACT, TEMPORARY, INTERNSHIP, FREELANCE or B2B; "
                    + "repeat to allow several")
            @RequestParam(required = false) List<EmploymentType> employmentType,
            @Parameter(description = "Minimum pay per hour. Monthly (/173.33) and yearly (/2080) rates are converted; "
                    + "CONTRACT_TOTAL jobs cannot be compared and are left out whenever a rate bound is set",
                    example = "40")
            @RequestParam(required = false) Double minRate,
            @Parameter(description = "Maximum pay per hour, same rules as minRate", example = "90")
            @RequestParam(required = false) Double maxRate,
            @Parameter(description = "Required-skill names (any case, exact); repeat or comma-separate. Max 20.")
            @RequestParam(required = false) List<String> skills,
            @Parameter(description = "ANY (default): the job needs at least one of the skills; ALL: every one")
            @RequestParam(required = false) String skillsMatch,
            @Parameter(description = "true (default) = open jobs, false = closed jobs, all = both")
            @RequestParam(required = false) String available,
            @Parameter(description = "newest (default), oldest, rateDesc, rateAsc or title. Rate sorts use the hourly "
                    + "equivalent and put contract totals last")
            @RequestParam(required = false) String sort,
            @Parameter(description = "Page number, starting at 0", example = "0")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size, 1 to 50 (default 20)", example = "20")
            @RequestParam(required = false) Integer size) {
        return searchService.search(JobSearchCriteria.of(q, location, locationId, workMode, employmentType, minRate, maxRate,
                skills, skillsMatch, available, sort, page, size));
    }
}
