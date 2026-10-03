package com.mcverse.jobify.application.service;

import com.mcverse.jobify.application.dto.ApplicationResponse;
import com.mcverse.jobify.application.dto.ApplyRequest;
import com.mcverse.jobify.application.model.Application;
import com.mcverse.jobify.application.model.ApplicationStatus;
import com.mcverse.jobify.application.repository.ApplicationRepository;
import com.mcverse.jobify.common.exception.BusinessRuleException;
import com.mcverse.jobify.common.exception.LicenseValidationException;
import com.mcverse.jobify.common.exception.ResourceNotFoundException;
import com.mcverse.jobify.job.repository.JobRepo;
import com.mcverse.jobify.model.JobPost;
import com.mcverse.jobify.user.model.Employer;
import com.mcverse.jobify.user.model.Seeker;
import com.mcverse.jobify.user.repository.SeekerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final ApplicationRepository applicationRepo;
    private final JobRepo jobRepo;
    private final SeekerRepository seekerRepo;

    public ApplicationService(ApplicationRepository applicationRepo, JobRepo jobRepo, SeekerRepository seekerRepo) {
        this.applicationRepo = applicationRepo;
        this.jobRepo = jobRepo;
        this.seekerRepo = seekerRepo;
    }

    @Transactional
    public ApplicationResponse apply(Integer jobId, ApplyRequest request, String username) {
        Seeker seeker = seekerRepo.findByUsername(username).orElseThrow(() -> {
            log.warn("Denied: user '{}' tried to apply for job {} without the SEEKER role", username, jobId);
            return new LicenseValidationException("Only job seekers can apply for jobs.");
        });
        JobPost job = jobRepo.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("JobPost", jobId.toString()));
        if (!job.isAvailable()) {
            throw new BusinessRuleException("This job is no longer accepting applications.");
        }
        String note = cleanNote(request);

        Application existing = applicationRepo.findBySeekerUsernameAndJobPostId(username, jobId).orElse(null);
        if (existing == null) {
            return toResponse(applicationRepo.save(new Application(seeker, job, note)));
        }
        if (existing.getStatus() != ApplicationStatus.WITHDRAWN) {
            throw new BusinessRuleException("You have already applied to this job.");
        }
        existing.reapply(note);
        return toResponse(applicationRepo.save(existing));
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> listMine(String username) {
        if (seekerRepo.findByUsername(username).isEmpty()) {
            throw new LicenseValidationException("Only job seekers have applications.");
        }
        return applicationRepo.findAllBySeekerUsernameOrderByCreatedAtDesc(username).stream()
                .map(this::toResponse).toList();
    }

    private static String cleanNote(ApplyRequest request) {
        if (request == null || request.coverNote() == null || request.coverNote().isBlank()) {
            return null;
        }
        return request.coverNote().trim();
    }

    ApplicationResponse toResponse(Application a) {
        JobPost job = a.getJob();
        Employer employer = job.getEmployer();
        String companyName = employer != null && employer.getCompany() != null
                ? employer.getCompany().getName() : null;
        return new ApplicationResponse(a.getId(), a.getStatus(), a.getCoverNote(), a.getCreatedAt(),
                a.getUpdatedAt(),
                new ApplicationResponse.JobSummary(job.getPostId(), job.getJobTitle(),
                        employer != null ? employer.getUsername() : null, companyName, job.isAvailable()));
    }
}
