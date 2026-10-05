package com.mcverse.jobify.account.service;

import com.mcverse.jobify.application.repository.ApplicationRepository;
import com.mcverse.jobify.job.repository.SavedJobRepository;
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

    public List<DeletionRequestResponse> listPending() {
        return deletionRequestRepository.findAllByStatusOrderByRequestedAtAsc(DeletionRequestStatus.PENDING)
                .stream().map(accountService::toResponse).toList();
    }

    @Transactional
    public DeletionRequestResponse approve(String requestId) {
        DeletionRequest request = pendingRequest(requestId);

        // Applications and saved jobs reference the seeker and the employer's jobs: remove them first, or the foreign key blocks the delete.
        switch (request.getRequesterRole()) {
            case SEEKER -> {
                applicationRepo.deleteBySeekerUsername(request.getUsername());
                savedJobRepo.deleteBySeekerUsername(request.getUsername());
                seekerRepo.findByUsername(request.getUsername()).ifPresent(seekerRepo::delete);
            }
            case EMPLOYER -> {
                applicationRepo.deleteByJobEmployerUsername(request.getUsername());
                savedJobRepo.deleteByJobEmployerUsername(request.getUsername());
                employerRepo.findByUsername(request.getUsername()).ifPresent(employerRepo::delete);
            }
            case ADMIN -> { /* no domain profile to remove */ }
        }
        authUserRepository.findByUsername(request.getUsername()).ifPresent(authUserRepository::delete);

        request.resolve(DeletionRequestStatus.APPROVED, null);
        return accountService.toResponse(deletionRequestRepository.save(request));
    }

    @Transactional
    public DeletionRequestResponse reject(String requestId, ResolveDeletionRequestRequest body) {
        DeletionRequest request = pendingRequest(requestId);
        request.resolve(DeletionRequestStatus.REJECTED, body.note());
        return accountService.toResponse(deletionRequestRepository.save(request));
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
