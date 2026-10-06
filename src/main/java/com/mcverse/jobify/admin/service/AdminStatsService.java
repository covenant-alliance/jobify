package com.mcverse.jobify.admin.service;

import com.mcverse.jobify.account.model.DeletionRequestStatus;
import com.mcverse.jobify.account.repository.DeletionRequestRepository;
import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.admin.dto.AdminStatsResponse;
import com.mcverse.jobify.application.model.ApplicationStatus;
import com.mcverse.jobify.application.repository.ApplicationRepository;
import com.mcverse.jobify.auth.model.Role;
import com.mcverse.jobify.auth.repository.AuthUserRepository;
import com.mcverse.jobify.job.repository.JobRepo;
import com.mcverse.jobify.user.repository.EmployerRepository;
import com.mcverse.jobify.user.repository.SeekerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminStatsService {

    private final AuthUserRepository authUserRepo;
    private final SeekerRepository seekerRepo;
    private final EmployerRepository employerRepo;
    private final JobRepo jobRepo;
    private final ApplicationRepository applicationRepo;
    private final DeletionRequestRepository deletionRequestRepo;
    private final Clock clock;
    private final AuditLog auditLog;

    public AdminStatsService(AuthUserRepository authUserRepo, SeekerRepository seekerRepo,
                             EmployerRepository employerRepo, JobRepo jobRepo, ApplicationRepository applicationRepo,
                             DeletionRequestRepository deletionRequestRepo, Clock clock, AuditLog auditLog) {
        this.authUserRepo = authUserRepo;
        this.seekerRepo = seekerRepo;
        this.employerRepo = employerRepo;
        this.jobRepo = jobRepo;
        this.applicationRepo = applicationRepo;
        this.deletionRequestRepo = deletionRequestRepo;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse stats(String adminUsername) {
        // Timestamps are stored in the server's local zone, so "now" must be read in the same zone.
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneId.systemDefault()));
        LocalDateTime weekAgo = now.minusDays(7);
        LocalDateTime monthAgo = now.minusDays(30);

        Map<String, Long> byRole = new LinkedHashMap<>();
        long totalUsers = 0;
        for (Role role : Role.values()) {
            long count = authUserRepo.countByRole(role);
            byRole.put(role.name(), count);
            totalUsers += count;
        }
        long newWeek = seekerRepo.countByCreationDateGreaterThanEqual(weekAgo)
                + employerRepo.countByCreationDateGreaterThanEqual(weekAgo);
        long newMonth = seekerRepo.countByCreationDateGreaterThanEqual(monthAgo)
                + employerRepo.countByCreationDateGreaterThanEqual(monthAgo);

        long open = jobRepo.countByAvailable(true);
        long closed = jobRepo.countByAvailable(false);

        Map<ApplicationStatus, Long> byStatus = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            byStatus.put(status, 0L);
        }
        long totalApplications = 0;
        List<Object[]> grouped = applicationRepo.countGroupedByStatus();
        for (Object[] row : grouped) {
            long count = ((Number) row[1]).longValue();
            byStatus.put((ApplicationStatus) row[0], count);
            totalApplications += count;
        }

        auditLog.record(adminUsername, "VIEW_STATS", "");
        return new AdminStatsResponse(
                new AdminStatsResponse.Users(totalUsers, byRole, newWeek, newMonth),
                new AdminStatsResponse.Jobs(open + closed, open, closed,
                        jobRepo.countByCreatedAtGreaterThanEqual(weekAgo)),
                new AdminStatsResponse.Applications(totalApplications, byStatus,
                        applicationRepo.countByCreatedAtGreaterThanEqual(weekAgo)),
                deletionRequestRepo.countByStatus(DeletionRequestStatus.PENDING));
    }
}
