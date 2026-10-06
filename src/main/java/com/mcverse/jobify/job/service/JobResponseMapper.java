package com.mcverse.jobify.job.service;

import com.mcverse.jobify.job.dto.JobPostResponse;
import com.mcverse.jobify.job.model.JobPost;
import com.mcverse.jobify.user.model.Company;
import com.mcverse.jobify.user.model.Employer;
import com.mcverse.jobify.user.model.Skill;
import com.mcverse.jobify.user.service.CompanyLogos;
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
                job.getRate(), job.getRateType(), employer != null ? employer.getUsername() : null,
                company != null ? company.getName() : null, company != null ? company.getId() : null,
                job.isAvailable(), job.getLocation(), job.getWorkMode(), job.getEmploymentType(),
                job.getCreatedAt(), requiredSkills, List.copyOf(job.getBenefits()),
                List.copyOf(job.getResponsibilities()), List.copyOf(job.getRequirements()),
                job.getImages().stream().map(i -> "/jobs/" + job.getPostId() + "/images/" + i.getId()).toList(),
                CompanyLogos.urlOf(company));
    }
}
