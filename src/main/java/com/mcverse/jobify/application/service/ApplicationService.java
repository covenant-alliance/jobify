package com.mcverse.jobify.application.service;

import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.application.dto.ApplicationResponse;
import com.mcverse.jobify.application.dto.ApplicationStatsResponse;
import com.mcverse.jobify.application.dto.ApplyRequest;
import com.mcverse.jobify.application.dto.JobApplicationResponse;
import com.mcverse.jobify.application.model.Application;
import com.mcverse.jobify.application.model.ApplicationStatus;
import com.mcverse.jobify.application.model.ApplicationStatusChange;
import com.mcverse.jobify.application.repository.ApplicationStatusChangeRepository;
import com.mcverse.jobify.application.repository.ApplicationRepository;
import com.mcverse.jobify.common.exception.BusinessRuleException;
import com.mcverse.jobify.common.exception.LicenseValidationException;
import com.mcverse.jobify.common.exception.ResourceNotFoundException;
import com.mcverse.jobify.job.model.JobPost;
import com.mcverse.jobify.job.repository.JobRepo;
import com.mcverse.jobify.user.model.Employer;
import com.mcverse.jobify.user.model.ActivityType;
import com.mcverse.jobify.user.service.CompanyAccess;
import com.mcverse.jobify.user.service.CompanyActivityService;
import com.mcverse.jobify.user.model.Seeker;
import com.mcverse.jobify.user.repository.SeekerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    /** Stages from which a seeker may still withdraw; REJECTED and WITHDRAWN are final. */
    private static final Set<ApplicationStatus> WITHDRAWABLE = EnumSet.of(ApplicationStatus.APPLIED,
            ApplicationStatus.IN_REVIEW, ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER);

    private final ApplicationRepository applicationRepo;
    private final JobRepo jobRepo;
    private final SeekerRepository seekerRepo;
    private final Clock clock;
    private final ApplicationNotifier notifier;
    private final AuditLog auditLog;
    private final ApplicationStatusChangeRepository historyRepo;
    private final CompanyAccess access;
    private final CompanyActivityService activity;

    public ApplicationService(ApplicationRepository applicationRepo, JobRepo jobRepo, SeekerRepository seekerRepo,
                              Clock clock, ApplicationNotifier notifier, AuditLog auditLog,
                              ApplicationStatusChangeRepository historyRepo, CompanyAccess access,
                              CompanyActivityService activity) {
        this.access = access;
        this.activity = activity;
        this.auditLog = auditLog;
        this.historyRepo = historyRepo;
        this.applicationRepo = applicationRepo;
        this.jobRepo = jobRepo;
        this.seekerRepo = seekerRepo;
        this.clock = clock;
        this.notifier = notifier;
    }

    @Transactional
    public ApplicationResponse apply(Integer jobId, ApplyRequest request, String username) {
        Seeker seeker = seekerRepo.findByUsername(username).orElseThrow(() -> {
            log.warn("Denied: user '{}' tried to apply for job {} without the SEEKER role", username, jobId);
            auditLog.event(username, "ACCESS_DENIED", "action='apply' job=" + jobId + " reason=not a seeker");
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
            Application created = applicationRepo.save(new Application(seeker, job, note));
            recordChange(created, null, ApplicationStatus.APPLIED, username);
            notifier.applicationSubmitted(created);
            return toResponse(created);
        }
        if (existing.getStatus() != ApplicationStatus.WITHDRAWN) {
            throw new BusinessRuleException("You have already applied to this job.");
        }
        existing.reapply(note);
        Application reopened = applicationRepo.save(existing);
        recordChange(reopened, ApplicationStatus.WITHDRAWN, ApplicationStatus.APPLIED, username);
        notifier.applicationSubmitted(reopened);
        return toResponse(reopened);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> listMine(String username) {
        if (seekerRepo.findByUsername(username).isEmpty()) {
            throw new LicenseValidationException("Only job seekers have applications.");
        }
        return applicationRepo.findAllBySeekerUsernameOrderByCreatedAtDesc(username).stream()
                .map(this::toResponse).toList();
    }

    /** Seeker withdraws their own application. The row is kept with status WITHDRAWN so history is not lost. */
    @Transactional
    public void withdraw(String applicationId, String username) {
        Application application = applicationRepo.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId));
        if (!application.getSeeker().getUsername().equals(username)) {
            log.warn("Denied: user '{}' tried to withdraw application {} owned by '{}'", username, applicationId,
                    application.getSeeker().getUsername());
            auditLog.event(username, "ACCESS_DENIED", "action='withdraw' application=" + applicationId + " owner="
                    + application.getSeeker().getUsername());
            throw new LicenseValidationException("You can only withdraw your own applications.");
        }
        if (!WITHDRAWABLE.contains(application.getStatus())) {
            throw new BusinessRuleException("This application can no longer be withdrawn.");
        }
        updateStatus(application, ApplicationStatus.WITHDRAWN, username);
    }

    /** Employer lists the applicants of a job they own, newest first, optionally only one stage. */
    @Transactional(readOnly = true)
    public List<JobApplicationResponse> listForJob(Integer jobId, ApplicationStatus status, String username) {
        JobPost job = jobRepo.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("JobPost", jobId.toString()));
        requireJobOwner(job, username, "view applications for", jobId);
        List<Application> applications = status == null
                ? applicationRepo.findAllByJobPostIdOrderByCreatedAtDesc(jobId)
                : applicationRepo.findAllByJobPostIdAndStatusOrderByCreatedAtDesc(jobId, status);
        return applications.stream().map(this::toJobApplicationResponse).toList();
    }

    /** Employer moves an application of one of their jobs to another stage. */
    @Transactional
    public JobApplicationResponse changeStatus(String applicationId, ApplicationStatus target, String username) {
        Application application = applicationRepo.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId));
        requireJobOwner(application.getJob(), username, "manage applications for", application.getJob().getPostId());

        ApplicationStatus current = application.getStatus();
        if (target == ApplicationStatus.WITHDRAWN) {
            throw new BusinessRuleException("Only the applicant can withdraw an application.");
        }
        if (current.employerTargets().isEmpty()) {
            throw new BusinessRuleException(
                    "This application is already " + current + " and can no longer change.");
        }
        if (!current.employerTargets().contains(target)) {
            throw new BusinessRuleException(
                    "An application in " + current + " cannot move to " + target + ".");
        }
        updateStatus(application, target, username);
        JobPost job = application.getJob();
        activity.record(job.getEmployer() == null ? null : job.getEmployer().getCompany(), username,
                ActivityType.APPLICATION_MOVED, username + " moved an application for \"" + job.getJobTitle()
                        + "\" from " + current + " to " + target, job.getPostId(), application.getId());
        return toJobApplicationResponse(application);
    }

    /**
     * The one place an application's status changes after it was created. Notifications and the status history both
     * hook in here, so keep every status change going through this method.
     */
    private void updateStatus(Application application, ApplicationStatus newStatus, String actor) {
        ApplicationStatus previous = application.getStatus();
        application.changeStatus(newStatus);
        applicationRepo.save(application);
        recordChange(application, previous, newStatus, actor);
        notifier.statusChanged(application, newStatus);
    }

    /** Adds a row to the application's history. Stamped with the application's own timestamp so the two agree. */
    private void recordChange(Application application, ApplicationStatus from, ApplicationStatus to, String actor) {
        historyRepo.save(new ApplicationStatusChange(application, from, to, application.getUpdatedAt(), actor));
    }

    private void requireJobOwner(JobPost job, String username, String action, Integer jobId) {
        Employer owner = job.getEmployer();
        if (!access.canManageDataOf(username, owner)) {
            log.warn("Denied: user '{}' tried to {} job {} owned by '{}'", username, action, jobId,
                    owner == null ? "nobody" : owner.getUsername());
            auditLog.event(username, "ACCESS_DENIED", "action='" + action + "' job=" + jobId + " owner="
                    + (owner == null ? "nobody" : owner.getUsername()));
            throw new LicenseValidationException("You can only " + action + " your own jobs.");
        }
    }

    /** Counts and a 12-week time series of the seeker's own applications, for the dashboard charts. */
    @Transactional(readOnly = true)
    public ApplicationStatsResponse statsForSeeker(String username) {
        if (seekerRepo.findByUsername(username).isEmpty()) {
            throw new LicenseValidationException("Only job seekers have applications.");
        }
        List<ApplicationStatsCalculator.Entry> entries = applicationRepo
                .findAllBySeekerUsernameOrderByCreatedAtDesc(username).stream()
                .map(a -> new ApplicationStatsCalculator.Entry(a.getStatus(), a.getCreatedAt()))
                .toList();
        // Timestamps are stored in the server's local zone, so "today" must be read in the same zone.
        return ApplicationStatsCalculator.calculate(entries, LocalDate.now(clock.withZone(ZoneId.systemDefault())));
    }

    private static String cleanNote(ApplyRequest request) {
        if (request == null || request.coverNote() == null || request.coverNote().isBlank()) {
            return null;
        }
        return request.coverNote().trim();
    }

    private JobApplicationResponse toJobApplicationResponse(Application a) {
        Seeker seeker = a.getSeeker();
        return new JobApplicationResponse(a.getId(), a.getStatus(), a.getCoverNote(), a.getCreatedAt(),
                a.getUpdatedAt(), new JobApplicationResponse.Applicant(seeker.getId(), seeker.getUsername(),
                        seeker.getName(), seeker.getLastName(), seeker.getCv() != null));
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
