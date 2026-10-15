package com.mcverse.jobify.admin.service;

import com.mcverse.jobify.account.service.AdminAccountService;
import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.application.repository.ApplicationRepository;
import com.mcverse.jobify.auth.model.AppUser;
import com.mcverse.jobify.auth.model.Role;
import com.mcverse.jobify.auth.repository.AuthUserRepository;
import com.mcverse.jobify.auth.service.RefreshTokenService;
import com.mcverse.jobify.common.exception.BusinessRuleException;
import com.mcverse.jobify.common.exception.ResourceNotFoundException;
import com.mcverse.jobify.job.model.JobPost;
import com.mcverse.jobify.job.repository.JobRepo;
import com.mcverse.jobify.job.repository.SavedJobRepository;
import com.mcverse.jobify.job.service.JobImageService;
import com.mcverse.jobify.notification.model.NotificationType;
import com.mcverse.jobify.notification.service.NotificationService;
import com.mcverse.jobify.user.model.Employer;
import com.mcverse.jobify.user.repository.EmployerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * What an administrator can do directly: switch an account off or on, delete an account, close or reopen a job,
 * delete a job. Everything is written to the audit log; changes to jobs tell the people who own them.
 */
@Service
public class AdminModerationService {

    private static final int REASON_MAX = 500;

    private final AuthUserRepository users;
    private final AdminAccountService accounts;
    private final JobRepo jobs;
    private final ApplicationRepository applications;
    private final SavedJobRepository savedJobs;
    private final JobImageService images;
    private final EmployerRepository employers;
    private final NotificationService notifications;
    private final AuditLog auditLog;
    private final RefreshTokenService refreshTokens;

    public AdminModerationService(AuthUserRepository users, AdminAccountService accounts, JobRepo jobs,
                                  ApplicationRepository applications, SavedJobRepository savedJobs,
                                  JobImageService images, EmployerRepository employers,
                                  NotificationService notifications, AuditLog auditLog,
                                  RefreshTokenService refreshTokens) {
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.accounts = accounts;
        this.jobs = jobs;
        this.applications = applications;
        this.savedJobs = savedJobs;
        this.images = images;
        this.employers = employers;
        this.notifications = notifications;
        this.auditLog = auditLog;
    }

    // ------------------------------------------------------------------------------------------ accounts

    @Transactional
    public boolean setEnabled(String admin, String username, boolean enabled, String reason) {
        AppUser user = requireManageable(admin, username, enabled ? "enable" : "disable");
        if (user.isEnabled() == enabled) {
            throw new BusinessRuleException("That account is already " + (enabled ? "enabled." : "disabled."));
        }
        user.setEnabled(enabled);
        users.save(user);
        if (!enabled) {
            refreshTokens.revokeAll(username); // no new access tokens for a disabled account
        }
        auditLog.record(admin, enabled ? "USER_ENABLED" : "USER_DISABLED",
                "account=" + username + (reason == null || reason.isBlank() ? "" : " reason='" + reason.trim() + "'"));
        return user.isEnabled();
    }

    @Transactional
    public void deleteAccount(String admin, String username, String reason) {
        String why = requireReason(reason);
        AppUser user = requireManageable(admin, username, "delete");
        accounts.removeAccount(username, user.getRole());
        auditLog.record(admin, "USER_DELETED", "account=" + username + " role=" + user.getRole() + " reason='" + why + "'");
    }

    private AppUser requireManageable(String admin, String username, String action) {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Account", username));
        if (user.getUsername().equals(admin)) {
            throw new BusinessRuleException("You cannot " + action + " your own account here.");
        }
        if (user.getRole() == Role.ADMIN) {
            throw new BusinessRuleException("Administrator accounts cannot be " + pastTense(action) + " here.");
        }
        return user;
    }

    private static String pastTense(String action) {
        return switch (action) {
            case "enable" -> "enabled";
            case "disable" -> "disabled";
            default -> "deleted";
        };
    }

    // ---------------------------------------------------------------------------------------------- jobs

    @Transactional
    public boolean setJobAvailable(String admin, Integer jobId, boolean available, String reason) {
        JobPost job = requireJob(jobId);
        if (!available) {
            requireReason(reason);
        }
        if (job.isAvailable() == available) {
            throw new BusinessRuleException("That job is already " + (available ? "open." : "closed."));
        }
        job.setAvailable(available);
        jobs.save(job);
        String note = reason == null || reason.isBlank() ? "" : " Reason: " + reason.trim();
        tellOwners(job, available ? "An administrator reopened your job" : "An administrator closed your job",
                "\"" + job.getJobTitle() + "\" was " + (available ? "reopened" : "closed") + " by an administrator." + note,
                jobId);
        auditLog.record(admin, available ? "JOB_REOPENED_BY_ADMIN" : "JOB_CLOSED_BY_ADMIN",
                "job=" + jobId + " reason='" + (reason == null ? "" : reason.trim()) + "'");
        return job.isAvailable();
    }

    @Transactional
    public void deleteJob(String admin, Integer jobId, String reason) {
        String why = requireReason(reason);
        JobPost job = requireJob(jobId);
        tellOwners(job, "An administrator removed your job",
                "\"" + job.getJobTitle() + "\" was removed by an administrator, together with its applications. Reason: "
                        + why, null);
        images.deleteFilesOfJob(jobId);
        applications.deleteByJobPostId(jobId);
        savedJobs.deleteByJobPostId(jobId);
        jobs.delete(job);
        auditLog.record(admin, "JOB_DELETED_BY_ADMIN", "job=" + jobId + " reason='" + why + "'");
    }

    private JobPost requireJob(Integer jobId) {
        return jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("JobPost", jobId));
    }

    /** Everyone in the job's company, or just the poster when there is no company. */
    private void tellOwners(JobPost job, String title, String body, Integer jobId) {
        Employer poster = job.getEmployer();
        if (poster == null) {
            return;
        }
        List<String> team = poster.getCompany() == null ? List.of(poster.getUsername())
                : employers.findAllByCompanyIdOrderByCreationDateAscIdAsc(poster.getCompany().getId()).stream()
                        .map(Employer::getUsername).toList();
        for (String member : team) {
            notifications.notify(member, NotificationType.SYSTEM, title, body, jobId, null);
        }
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required.");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > REASON_MAX) {
            throw new IllegalArgumentException("reason must be at most " + REASON_MAX + " characters.");
        }
        return trimmed;
    }
}
