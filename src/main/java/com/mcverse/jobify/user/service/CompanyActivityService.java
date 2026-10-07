package com.mcverse.jobify.user.service;

import com.mcverse.jobify.user.model.ActivityType;
import com.mcverse.jobify.user.model.Company;
import com.mcverse.jobify.user.model.CompanyActivity;
import com.mcverse.jobify.user.repository.CompanyActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes the company activity feed. It joins the caller's transaction, so the feed never shows what rolled back. */
@Service
public class CompanyActivityService {

    private static final int SUMMARY_MAX = 400;

    private final CompanyActivityRepository repository;

    public CompanyActivityService(CompanyActivityRepository repository) {
        this.repository = repository;
    }

    /** Records an action for the company; does nothing when {@code company} is null (a person without a company). */
    @Transactional
    public void record(Company company, String actor, ActivityType type, String summary, Integer jobId,
                       String applicationId) {
        if (company != null) {
            record(company.getId(), actor, type, summary, jobId, applicationId);
        }
    }

    @Transactional
    public void record(String companyId, String actor, ActivityType type, String summary, Integer jobId,
                       String applicationId) {
        String text = summary.length() <= SUMMARY_MAX ? summary : summary.substring(0, SUMMARY_MAX - 3) + "...";
        repository.save(new CompanyActivity(companyId, actor, type, text, jobId, applicationId));
    }
}
