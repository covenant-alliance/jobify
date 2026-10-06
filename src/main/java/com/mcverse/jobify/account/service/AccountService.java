package com.mcverse.jobify.account.service;

import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.account.dto.ChangePasswordRequest;
import com.mcverse.jobify.account.dto.DeletionRequestRequest;
import com.mcverse.jobify.account.dto.DeletionRequestResponse;
import com.mcverse.jobify.account.model.DeletionRequest;
import com.mcverse.jobify.account.model.DeletionRequestStatus;
import com.mcverse.jobify.account.repository.DeletionRequestRepository;
import com.mcverse.jobify.auth.model.AppUser;
import com.mcverse.jobify.auth.model.Role;
import com.mcverse.jobify.auth.repository.AuthUserRepository;
import com.mcverse.jobify.common.exception.BusinessRuleException;
import com.mcverse.jobify.common.exception.ResourceNotFoundException;
import com.mcverse.jobify.auth.security.PasswordPolicy;
import com.mcverse.jobify.notification.model.NotificationType;
import com.mcverse.jobify.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);


    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private DeletionRequestRepository deletionRequestRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PasswordPolicy passwordPolicy;
    @Autowired private NotificationService notifications;
    @Autowired private AuditLog auditLog;

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        AppUser user = authUserRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Account", username));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            log.warn("Password change refused for '{}': current password is incorrect", username);
            auditLog.event(username, "PASSWORD_CHANGE_REFUSED", "current password incorrect");
            throw new BusinessRuleException("Current password is incorrect.");
        }
        passwordPolicy.validateChange(username, request.currentPassword(), request.newPassword());
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        authUserRepository.save(user);
        log.info("Password changed for '{}'", username);
        auditLog.event(username, "PASSWORD_CHANGED", "");
        notifications.notify(username, NotificationType.ACCOUNT, "Your password was changed",
                "The password for your account was just changed. If this was not you, contact support.",
                null, null);
    }

    @Transactional
    public DeletionRequestResponse requestDeletion(String username, Role role, DeletionRequestRequest request) {
        deletionRequestRepository.findFirstByUsernameAndStatusOrderByRequestedAtDesc(username, DeletionRequestStatus.PENDING)
                .ifPresent(existing -> {
                    throw new BusinessRuleException("You already have a pending deletion request.");
                });
        DeletionRequest saved = deletionRequestRepository.save(
                new DeletionRequest(username, role, request.reason()));
        auditLog.event(username, "DELETION_REQUESTED", "role=" + role);
        return toResponse(saved);
    }

    public DeletionRequestResponse getMyDeletionRequest(String username) {
        return deletionRequestRepository.findFirstByUsernameAndStatusOrderByRequestedAtDesc(username, DeletionRequestStatus.PENDING)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional
    public void cancelMyDeletionRequest(String username) {
        DeletionRequest pending = deletionRequestRepository
                .findFirstByUsernameAndStatusOrderByRequestedAtDesc(username, DeletionRequestStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("Deletion request", username));
        deletionRequestRepository.delete(pending);
        auditLog.event(username, "DELETION_CANCELLED", "");
    }

    public DeletionRequestResponse toResponse(DeletionRequest r) {
        return new DeletionRequestResponse(r.getId(), r.getUsername(), r.getRequesterRole(), r.getReason(),
                r.getStatus(), r.getRequestedAt(), r.getResolvedAt(), r.getResolutionNote());
    }
}
