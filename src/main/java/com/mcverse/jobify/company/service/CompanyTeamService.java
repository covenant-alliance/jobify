package com.mcverse.jobify.company.service;

import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.common.exception.BusinessRuleException;
import com.mcverse.jobify.common.exception.LicenseValidationException;
import com.mcverse.jobify.common.exception.ResourceNotFoundException;
import com.mcverse.jobify.company.dto.InvitationResponse;
import com.mcverse.jobify.company.dto.MemberResponse;
import com.mcverse.jobify.company.model.CompanyInvitation;
import com.mcverse.jobify.company.model.InvitationStatus;
import com.mcverse.jobify.company.repository.CompanyInvitationRepository;
import com.mcverse.jobify.job.service.CompanyHandover;
import com.mcverse.jobify.notification.model.NotificationType;
import com.mcverse.jobify.notification.service.NotificationService;
import com.mcverse.jobify.user.model.ActivityType;
import com.mcverse.jobify.user.model.Company;
import com.mcverse.jobify.user.model.CompanyRole;
import com.mcverse.jobify.user.model.Employer;
import com.mcverse.jobify.user.repository.CompanyRepository;
import com.mcverse.jobify.user.repository.EmployerRepository;
import com.mcverse.jobify.user.service.CompanyActivityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Who is in a company: invitations, members, leaving, roles. Only an owner changes the team; the person invited
 * answers their own invitation. Everything is recorded on the audit log, the company's activity feed and, where a
 * person is affected, as a notification (type SYSTEM, so no new value for the front end to learn).
 */
@Service
public class CompanyTeamService {

    private final CompanyRepository companies;
    private final EmployerRepository employers;
    private final CompanyInvitationRepository invitations;
    private final NotificationService notifications;
    private final CompanyActivityService activity;
    private final CompanyHandover handover;
    private final AuditLog auditLog;
    private final Clock clock;

    public CompanyTeamService(CompanyRepository companies, EmployerRepository employers,
                              CompanyInvitationRepository invitations, NotificationService notifications,
                              CompanyActivityService activity, CompanyHandover handover, AuditLog auditLog,
                              Clock clock) {
        this.companies = companies;
        this.employers = employers;
        this.invitations = invitations;
        this.notifications = notifications;
        this.activity = activity;
        this.handover = handover;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneId.systemDefault()));
    }

    // ------------------------------------------------------------------------------------------ invitations

    @Transactional
    public InvitationResponse invite(String ownerName, String companyId, String inviteeName) {
        Employer owner = requireOwner(ownerName, companyId);
        Company company = owner.getCompany();
        String name = inviteeName.trim();
        if (name.equals(ownerName)) {
            throw new BusinessRuleException("You cannot invite yourself.");
        }
        Employer invitee = employers.findByUsername(name)
                .orElseThrow(() -> new BusinessRuleException("No employer account is named '" + name + "'."));
        if (invitee.getCompany() != null) {
            throw new BusinessRuleException(invitee.getCompany().getId().equals(companyId)
                    ? name + " is already a member of your company."
                    : name + " already belongs to a company.");
        }
        LocalDateTime now = now();
        for (CompanyInvitation old : invitations.findByCompanyIdAndInviteeUsernameAndStatus(companyId, name,
                InvitationStatus.PENDING)) {
            if (old.isOpen(now)) {
                throw new BusinessRuleException(name + " already has a pending invitation.");
            }
            old.close(InvitationStatus.EXPIRED, now); // an old, unanswered one makes room for a new one
        }
        CompanyInvitation saved = invitations.save(new CompanyInvitation(company, name, ownerName, now));
        notifications.notify(name, NotificationType.SYSTEM, "Invitation to join " + company.getName(),
                ownerName + " invited you to join " + company.getName()
                        + " on Jobify. You can accept or decline it in your invitations.", null, null);
        activity.record(company, ownerName, ActivityType.INVITATION_SENT,
                ownerName + " invited " + name + " to the company", null, null);
        auditLog.event(ownerName, "INVITATION_SENT", "company=" + companyId + " invitee=" + name);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> pendingFor(String ownerName, String companyId) {
        requireOwner(ownerName, companyId);
        LocalDateTime now = now();
        return invitations.findByCompanyIdAndStatusOrderByCreatedAtDesc(companyId, InvitationStatus.PENDING).stream()
                .filter(i -> i.isOpen(now)).map(this::toResponse).toList();
    }

    @Transactional
    public void cancel(String ownerName, String companyId, String invitationId) {
        requireOwner(ownerName, companyId);
        CompanyInvitation invitation = invitations.findById(invitationId)
                .filter(i -> i.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", invitationId));
        requireAnswerable(invitation);
        invitation.close(InvitationStatus.CANCELLED, now());
        auditLog.event(ownerName, "INVITATION_CANCELLED", "company=" + companyId + " invitee="
                + invitation.getInviteeUsername());
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> mine(String username) {
        requireEmployer(username, "have company invitations");
        LocalDateTime now = now();
        return invitations.findByInviteeUsernameAndStatusOrderByCreatedAtDesc(username, InvitationStatus.PENDING)
                .stream().filter(i -> i.isOpen(now)).map(this::toResponse).toList();
    }

    @Transactional
    public InvitationResponse accept(String username, String invitationId) {
        Employer person = requireEmployer(username, "answer company invitations");
        CompanyInvitation invitation = ownInvitation(username, invitationId);
        requireAnswerable(invitation);
        if (person.getCompany() != null) {
            throw new BusinessRuleException("You already belong to a company. Leave it before joining another.");
        }
        LocalDateTime now = now();
        Company company = invitation.getCompany();
        person.joinCompany(company, CompanyRole.MANAGER);
        invitation.close(InvitationStatus.ACCEPTED, now);
        // any other open invitations of this person are now moot
        for (CompanyInvitation other : invitations.findByInviteeUsernameAndStatusOrderByCreatedAtDesc(username,
                InvitationStatus.PENDING)) {
            if (!other.getId().equals(invitationId)) {
                other.close(InvitationStatus.CANCELLED, now);
            }
        }
        for (Employer owner : ownersOf(company.getId())) {
            notifications.notify(owner.getUsername(), NotificationType.SYSTEM, "A new colleague joined",
                    username + " accepted the invitation and joined " + company.getName() + " as a manager.",
                    null, null);
        }
        activity.record(company, username, ActivityType.MEMBER_JOINED,
                username + " joined the company as a manager", null, null);
        auditLog.event(username, "INVITATION_ACCEPTED", "company=" + company.getId());
        return toResponse(invitation);
    }

    @Transactional
    public InvitationResponse decline(String username, String invitationId) {
        requireEmployer(username, "answer company invitations");
        CompanyInvitation invitation = ownInvitation(username, invitationId);
        requireAnswerable(invitation);
        invitation.close(InvitationStatus.DECLINED, now());
        notifications.notify(invitation.getInvitedBy(), NotificationType.SYSTEM, "Invitation declined",
                username + " declined your invitation to join " + invitation.getCompany().getName() + ".", null, null);
        auditLog.event(username, "INVITATION_DECLINED", "company=" + invitation.getCompany().getId());
        return toResponse(invitation);
    }

    // --------------------------------------------------------------------------------------------- members

    @Transactional(readOnly = true)
    public List<MemberResponse> members(String username, String companyId) {
        requireMember(username, companyId);
        return employers.findAllByCompanyIdOrderByCreationDateAscIdAsc(companyId).stream()
                .map(e -> new MemberResponse(e.getUsername(), e.getName(), e.getLastName(), e.getCompanyRole(),
                        e.getCompanyJoinedAt()))
                .sorted(java.util.Comparator.comparing(MemberResponse::joinedAt)).toList();
    }

    @Transactional
    public void removeMember(String ownerName, String companyId, String targetName) {
        Employer owner = requireOwner(ownerName, companyId);
        if (targetName.equals(ownerName)) {
            throw new BusinessRuleException("To leave your own company use \"leave\" instead of removing yourself.");
        }
        Employer target = employers.findByUsername(targetName)
                .filter(e -> e.getCompany() != null && e.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Member", targetName));
        Company company = owner.getCompany();
        String companyName = company.getName();
        target.leaveCompany();
        employers.save(target);
        notifications.notify(targetName, NotificationType.SYSTEM, "You were removed from " + companyName,
                ownerName + " removed you from " + companyName + ". The jobs you posted stay with the company.",
                null, null);
        activity.record(companyId, ownerName, ActivityType.MEMBER_REMOVED,
                ownerName + " removed " + targetName + " from the company", null, null);
        auditLog.event(ownerName, "MEMBER_REMOVED", "company=" + companyId + " member=" + targetName);
        handover.passJobs(target, owner); // last: it detaches everything loaded so far
    }

    @Transactional
    public void leave(String username) {
        Employer person = requireEmployer(username, "leave a company");
        if (person.getCompany() == null) {
            throw new BusinessRuleException("You are not part of a company.");
        }
        String companyId = person.getCompany().getId();
        String companyName = person.getCompany().getName();
        List<Employer> colleagues = employers.findAllByCompanyIdOrderByCreationDateAscIdAsc(companyId).stream()
                .filter(e -> !e.getUsername().equals(username)).toList();
        Employer successor = colleagues.stream().filter(e -> e.getCompanyRole() == CompanyRole.OWNER).findFirst()
                .orElse(null);
        if (successor == null) {
            throw new BusinessRuleException(colleagues.isEmpty()
                    ? "You are the only person in this company, so you cannot leave it."
                    : "You are the only owner. Make a colleague an owner first, then leave.");
        }
        person.leaveCompany();
        employers.save(person);
        for (Employer owner : colleagues.stream().filter(e -> e.getCompanyRole() == CompanyRole.OWNER).toList()) {
            notifications.notify(owner.getUsername(), NotificationType.SYSTEM, "A colleague left",
                    username + " left " + companyName + ". The jobs they posted stay with the company.", null, null);
        }
        activity.record(companyId, username, ActivityType.MEMBER_LEFT, username + " left the company", null, null);
        auditLog.event(username, "MEMBER_LEFT", "company=" + companyId);
        handover.passJobs(person, successor); // last: it detaches everything loaded so far
    }

    @Transactional
    public MemberResponse changeRole(String ownerName, String companyId, String targetName, CompanyRole role) {
        requireOwner(ownerName, companyId);
        Employer target = employers.findByUsername(targetName)
                .filter(e -> e.getCompany() != null && e.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Member", targetName));
        if (target.getCompanyRole() == role) {
            return toMember(target);
        }
        if (role == CompanyRole.MANAGER && ownersOf(companyId).size() < 2) {
            throw new BusinessRuleException("A company needs at least one owner.");
        }
        target.setCompanyRole(role);
        employers.save(target);
        if (!targetName.equals(ownerName)) {
            notifications.notify(targetName, NotificationType.SYSTEM, "Your role changed",
                    ownerName + " made you " + (role == CompanyRole.OWNER ? "an owner" : "a manager") + " of "
                            + target.getCompany().getName() + ".", null, null);
        }
        activity.record(companyId, ownerName, ActivityType.ROLE_CHANGED,
                ownerName + " made " + targetName + " " + (role == CompanyRole.OWNER ? "an owner" : "a manager"),
                null, null);
        auditLog.event(ownerName, "ROLE_CHANGED", "company=" + companyId + " member=" + targetName + " role=" + role);
        return toMember(target);
    }

    // ------------------------------------------------------------------------------------------- helpers

    private List<Employer> ownersOf(String companyId) {
        return employers.findAllByCompanyIdOrderByCreationDateAscIdAsc(companyId).stream()
                .filter(e -> e.getCompanyRole() == CompanyRole.OWNER).toList();
    }

    private Employer requireEmployer(String username, String action) {
        return employers.findByUsername(username)
                .orElseThrow(() -> new LicenseValidationException("Only employers can " + action + "."));
    }

    /** The caller, if they belong to this company; 404 for an unknown company, 403 for an outsider. */
    public Employer requireMember(String username, String companyId) {
        if (!companies.existsById(companyId)) {
            throw new ResourceNotFoundException("Company", companyId);
        }
        Employer person = employers.findByUsername(username).orElse(null);
        if (person == null || person.getCompany() == null || !person.getCompany().getId().equals(companyId)) {
            auditLog.event(username, "ACCESS_DENIED", "action='view company team' company=" + companyId);
            throw new LicenseValidationException("You are not part of this company.");
        }
        return person;
    }

    private Employer requireOwner(String username, String companyId) {
        Employer person = requireMember(username, companyId);
        if (person.getCompanyRole() != CompanyRole.OWNER) {
            auditLog.event(username, "ACCESS_DENIED", "action='manage company team' company=" + companyId);
            throw new LicenseValidationException("Only the company owner can do that.");
        }
        return person;
    }

    /** The invitation, which only the invited person may touch: 404 if it does not exist, 403 if it is not theirs. */
    private CompanyInvitation ownInvitation(String username, String invitationId) {
        CompanyInvitation invitation = invitations.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", invitationId));
        if (!invitation.getInviteeUsername().equals(username)) {
            auditLog.event(username, "ACCESS_DENIED", "action='answer invitation' invitation=" + invitationId);
            throw new LicenseValidationException("This invitation is not addressed to you.");
        }
        return invitation;
    }

    private void requireAnswerable(CompanyInvitation invitation) {
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BusinessRuleException("This invitation was already "
                    + invitation.getStatus().name().toLowerCase(java.util.Locale.ROOT) + ".");
        }
        if (!invitation.isOpen(now())) {
            invitation.close(InvitationStatus.EXPIRED, now());
            throw new BusinessRuleException("This invitation has expired. Ask for a new one.");
        }
    }

    private MemberResponse toMember(Employer e) {
        return new MemberResponse(e.getUsername(), e.getName(), e.getLastName(), e.getCompanyRole(),
                e.getCompanyJoinedAt());
    }

    private InvitationResponse toResponse(CompanyInvitation i) {
        return new InvitationResponse(i.getId(), i.getCompany().getId(), i.getCompany().getName(),
                i.getInviteeUsername(), i.getInvitedBy(), i.getStatus(), i.getCreatedAt(), i.getExpiresAt(),
                i.getRespondedAt());
    }
}
