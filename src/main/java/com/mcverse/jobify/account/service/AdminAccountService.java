package com.mcverse.jobify.account.service;

import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.application.repository.ApplicationRepository;
import com.mcverse.jobify.job.repository.SavedJobRepository;
import com.mcverse.jobify.notification.model.NotificationType;
import com.mcverse.jobify.notification.service.NotificationService;
import com.mcverse.jobify.account.dto.DeletionRequestResponse;
import com.mcverse.jobify.account.dto.ResolveDeletionRequestRequest;
import com.mcverse.jobify.account.model.DeletionRequest;
import com.mcverse.jobify.account.model.DeletionRequestStatus;
import com.mcverse.jobify.account.repository.DeletionRequestRepository;
import com.mcverse.jobify.auth.repository.AuthUserRepository;
import com.mcverse.jobify.common.exception.BusinessRuleException;
import com.mcverse.jobify.common.exception.ResourceNotFoundException;
import com.mcverse.jobify.user.repository.EmployerRepository;
import com.mcverse.jobify.user.repository.SeekerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminAccountService {

    @Autowired private DeletionRequestRepository deletionRequestRepository;
    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private SeekerRepository seekerRepo;
    @Autowired private EmployerRepository employerRepo;
    @Autowired private AccountService accountService;
    @Autowired private ApplicationRepository applicationRepo;
    @Autowired private SavedJobRepository savedJobRepo;
    @Autowired private NotificationService notifications;
    @Autowired private AuditLog auditLog;
    @Autowired private com.mcverse.jobify.job.service.CompanyHandover companyHandover;
    @Autowired private com.mcverse.jobify.preference.service.PreferenceService preferences;
    @Autowired private com.mcverse.jobify.job.service.JobImageService jobImages;
    @Autowired private com.mcverse.jobify.user.service.CompanyLogoService companyLogos;

    public List<DeletionRequestResponse> listPending() {
        return deletionRequestRepository.findAllByStatusOrderByRequestedAtAsc(DeletionRequestStatus.PENDING)
                .stream().map(accountService::toResponse).toList();
    }

    @Transactional
    public DeletionRequestResponse approve(String requestId, String adminUsername) {
        DeletionRequest request = pendingRequest(requestId);

        // Applications and saved jobs reference the seeker and the employer's jobs: remove them first, or the foreign key blocks the delete.
        switch (request.getRequesterRole()) {
            case SEEKER -> {
                applicationRepo.deleteBySeekerUsername(request.getUsername());
                savedJobRepo.deleteBySeekerUsername(request.getUsername());
                seekerRepo.findByUsername(request.getUsername()).ifPresent(seekerRepo::delete);
            }
            case EMPLOYER -> {
                // a company with other people keeps its jobs, applications and logo: they pass to a colleague
                if (!companyHandover.handOverToColleague(request.getUsername())) {
                    applicationRepo.deleteByJobEmployerUsername(request.getUsername());
                    savedJobRepo.deleteByJobEmployerUsername(request.getUsername());
                    jobImages.deleteFilesOfEmployer(request.getUsername());
                    companyLogos.dropLogoOfEmployer(request.getUsername());
                }
                employerRepo.findByUsername(request.getUsername()).ifPresent(employerRepo::delete);
            }
            case ADMIN -> { /* no domain profile to remove */ }
        }
        notifications.deleteAllFor(request.getUsername());
        preferences.deleteFor(request.getUsername());
        authUserRepository.findByUsername(request.getUsername()).ifPresent(authUserRepository::delete);

        request.resolve(DeletionRequestStatus.APPROVED, null);
        auditLog.record(adminUsername, "APPROVE_DELETION", "request=" + requestId + " account=" + request.getUsername()
                + " role=" + request.getRequesterRole());
        return accountService.toResponse(deletionRequestRepository.save(request));
    }

    @Transactional
    public DeletionRequestResponse reject(String requestId, ResolveDeletionRequestRequest body, String adminUsername) {
        DeletionRequest request = pendingRequest(requestId);
        request.resolve(DeletionRequestStatus.REJECTED, body.note());
        DeletionRequest saved = deletionRequestRepository.save(request);
        auditLog.record(adminUsername, "REJECT_DELETION", "request=" + requestId + " account=" + request.getUsername());
        String reason = body.note() == null || body.note().isBlank() ? "" : " Note from the admin: " + body.note();
        notifications.notify(request.getUsername(), NotificationType.ACCOUNT, "Account deletion request declined",
                "Your request to delete your account was declined, so your account stays active." + reason,
                null, null);
        return accountService.toResponse(saved);
    }

    private DeletionRequest pendingRequest(String requestId) {
        DeletionRequest request = deletionRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Deletion request", requestId));
        if (request.getStatus() != DeletionRequestStatus.PENDING) {
            throw new BusinessRuleException("This request has already been resolved.");
        }
        return request;
    }
}
