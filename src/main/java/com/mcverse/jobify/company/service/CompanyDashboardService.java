package com.mcverse.jobify.company.service;

import com.mcverse.jobify.application.dto.EmployerStatsResponse;
import com.mcverse.jobify.application.service.EmployerStatsService;
import com.mcverse.jobify.company.dto.ActivityResponse;
import com.mcverse.jobify.user.repository.CompanyActivityRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** What the people of a company see together: the numbers across its jobs and the feed of what the team did. */
@Service
public class CompanyDashboardService {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    private final CompanyTeamService team;
    private final EmployerStatsService stats;
    private final CompanyActivityRepository activity;

    public CompanyDashboardService(CompanyTeamService team, EmployerStatsService stats,
                                   CompanyActivityRepository activity) {
        this.team = team;
        this.stats = stats;
        this.activity = activity;
    }

    /** Jobs, applications, weekly counts and funnel across every job of the company. Members only. */
    @Transactional(readOnly = true)
    public EmployerStatsResponse stats(String username, String companyId) {
        team.requireMember(username, companyId);
        return stats.statsFor(username); // already company-wide for a person in a company
    }

    /** Newest first. Members only. */
    @Transactional(readOnly = true)
    public List<ActivityResponse> activity(String username, String companyId, Integer limit) {
        team.requireMember(username, companyId);
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT + ".");
        }
        return activity.findByCompanyIdOrderByCreatedAtDescIdDesc(companyId, PageRequest.of(0, size)).stream()
                .map(a -> new ActivityResponse(a.getId(), a.getType(), a.getActor(), a.getSummary(), a.getJobId(),
                        a.getApplicationId(), a.getCreatedAt())).toList();
    }
}
