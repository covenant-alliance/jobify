package com.mcverse.jobify.application.service;

import com.mcverse.jobify.application.dto.ApplicationStatsResponse;
import com.mcverse.jobify.application.dto.EmployerStatsResponse;
import com.mcverse.jobify.application.model.Application;
import com.mcverse.jobify.application.repository.ApplicationRepository;
import com.mcverse.jobify.application.repository.ApplicationStatusChangeRepository;
import com.mcverse.jobify.common.exception.LicenseValidationException;
import com.mcverse.jobify.job.model.JobPost;
import com.mcverse.jobify.job.repository.JobRepo;
import com.mcverse.jobify.user.repository.EmployerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmployerStatsService {

    private final EmployerRepository employerRepo;
    private final JobRepo jobRepo;
    private final ApplicationRepository applicationRepo;
    private final ApplicationStatusChangeRepository historyRepo;
    private final Clock clock;

    public EmployerStatsService(EmployerRepository employerRepo, JobRepo jobRepo,
                                ApplicationRepository applicationRepo, ApplicationStatusChangeRepository historyRepo,
                                Clock clock) {
        this.historyRepo = historyRepo;
        this.employerRepo = employerRepo;
        this.jobRepo = jobRepo;
        this.applicationRepo = applicationRepo;
        this.clock = clock;
    }

    /** Numbers for the employer's jobs and those of their company's other members: per job, per status and per week. */
    @Transactional(readOnly = true)
    public EmployerStatsResponse statsFor(String username) {
        if (employerRepo.findByUsername(username).isEmpty()) {
            throw new LicenseValidationException("Only employers have job statistics.");
        }
        List<JobPost> jobs = jobRepo.findAllManagedBy(username);
        List<Application> applications = applicationRepo.findAllManagedBy(username);
        // Timestamps are stored in the server's local zone, so "today" must be read in the same zone.
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.systemDefault()));

        Map<Integer, List<ApplicationStatsCalculator.Entry>> byJob = new HashMap<>();
        List<ApplicationStatsCalculator.Entry> all = new ArrayList<>();
        for (Application application : applications) {
            ApplicationStatsCalculator.Entry entry =
                    new ApplicationStatsCalculator.Entry(application.getStatus(), application.getCreatedAt());
            all.add(entry);
            byJob.computeIfAbsent(application.getJob().getPostId(), id -> new ArrayList<>()).add(entry);
        }

        // every application's recorded history, grouped by application, then assembled per job and overall
        Map<String, List<ApplicationFunnelCalculator.Change>> changesByApplication = new HashMap<>();
        for (ApplicationStatusChangeRepository.Row row : historyRepo.findRowsManagedBy(username)) {
            changesByApplication.computeIfAbsent(row.applicationId(), id -> new ArrayList<>())
                    .add(new ApplicationFunnelCalculator.Change(row.from(), row.to(), row.at()));
        }
        Map<Integer, List<ApplicationFunnelCalculator.History>> historiesByJob = new HashMap<>();
        List<ApplicationFunnelCalculator.History> allHistories = new ArrayList<>();
        for (Application application : applications) {
            ApplicationFunnelCalculator.History history = new ApplicationFunnelCalculator.History(
                    application.getStatus(), application.getCreatedAt(),
                    changesByApplication.getOrDefault(application.getId(), List.of()));
            allHistories.add(history);
            historiesByJob.computeIfAbsent(application.getJob().getPostId(), id -> new ArrayList<>()).add(history);
        }

        ApplicationStatsResponse overall = ApplicationStatsCalculator.calculate(all, today);
        List<EmployerStatsResponse.JobStats> perJob = jobs.stream()
                .sorted(Comparator.comparing(JobPost::getPostId).reversed())
                .map(job -> {
                    ApplicationStatsResponse stats = ApplicationStatsCalculator.calculate(
                            byJob.getOrDefault(job.getPostId(), List.of()), today);
                    return new EmployerStatsResponse.JobStats(job.getPostId(), job.getJobTitle(), job.isAvailable(),
                            job.getCreatedAt(), stats.total(), stats.active(), stats.byStatus(),
                            ApplicationFunnelCalculator.calculate(historiesByJob.getOrDefault(job.getPostId(), List.of())));
                })
                .toList();

        int open = (int) jobs.stream().filter(JobPost::isAvailable).count();
        return new EmployerStatsResponse(
                new EmployerStatsResponse.Jobs(jobs.size(), open, jobs.size() - open),
                new EmployerStatsResponse.Applications(overall.total(), overall.active(), overall.byStatus(),
                        overall.weekly(), ApplicationFunnelCalculator.calculate(allHistories)),
                perJob);
    }
}
