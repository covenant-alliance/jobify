package com.mcverse.jobify.job.service;

import com.mcverse.jobify.job.dto.JobPostResponse;
import com.mcverse.jobify.job.model.RateType;
import com.mcverse.jobify.model.JobPost;
import com.mcverse.jobify.model.Skill;
import com.mcverse.jobify.user.model.Company;
import com.mcverse.jobify.user.model.Employer;
import org.springframework.stereotype.Component;

import java.util.List;

/** Turns a {@link JobPost} into its API representation. Call inside a transaction: it reads lazy relations. */
@Component
public class JobResponseMapper {

    public JobPostResponse toResponse(JobPost job) {
        Employer employer = job.getEmployer();
        Company company = employer != null ? employer.getCompany() : null;
        List<String> requiredSkills = job.getRequiredSkills().stream().map(Skill::getName).toList();
        return new JobPostResponse(job.getPostId(), job.getJobTitle(), job.getJobDescription(),
                rateOrHourly(job), job.getRateType() != null ? job.getRateType() : RateType.HOURLY,
                job.getHourlyRate(), employer != null ? employer.getUsername() : null,
                company != null ? company.getName() : null, company != null ? company.getId() : null,
                job.isAvailable(), job.getLocation(), job.getWorkMode(), job.getEmploymentType(),
                job.getCreatedAt(), requiredSkills);
    }

    /** Rows not yet backfilled fall back to their old hourly rate, so responses are never empty. */
    private static double rateOrHourly(JobPost job) {
        return job.getRate() != null ? job.getRate() : job.getHourlyRate();
    }
}
