package com.mcverse.jobify.application.service;

import com.mcverse.jobify.application.model.Application;
import com.mcverse.jobify.application.model.ApplicationStatus;
import com.mcverse.jobify.application.model.ApplicationStatusChange;
import com.mcverse.jobify.application.repository.ApplicationRepository;
import com.mcverse.jobify.application.repository.ApplicationStatusChangeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gives applications that were made before status history existed a minimal history, so the funnel has something to
 * work with. Safe on every boot: it only touches applications that have no history rows, and adds rows only.
 *
 * <p>What can honestly be inferred: the application was created (APPLIED, at its creation time) and, if it is no longer
 * APPLIED, it moved on to its current status at its last update. The stages in between, and the stage a rejected or
 * withdrawn application left from, were never recorded, so those rows say "from APPLIED" (the only thing known) and
 * are marked {@value #BACKFILL_ACTOR}. Applications made from now on get exact history.
 */
@Component
@Order(110)
public class ApplicationHistoryBackfill implements ApplicationRunner {

    static final String BACKFILL_ACTOR = "history-backfill";

    private static final Logger log = LoggerFactory.getLogger(ApplicationHistoryBackfill.class);

    private final ApplicationRepository applicationRepo;
    private final ApplicationStatusChangeRepository historyRepo;

    public ApplicationHistoryBackfill(ApplicationRepository applicationRepo,
                                      ApplicationStatusChangeRepository historyRepo) {
        this.applicationRepo = applicationRepo;
        this.historyRepo = historyRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Application> missing = applicationRepo.findAllWithoutHistory();
        for (Application application : missing) {
            historyRepo.save(new ApplicationStatusChange(application, null, ApplicationStatus.APPLIED,
                    application.getCreatedAt(), application.getSeeker().getUsername()));
            if (application.getStatus() != ApplicationStatus.APPLIED) {
                historyRepo.save(new ApplicationStatusChange(application, ApplicationStatus.APPLIED,
                        application.getStatus(), application.getUpdatedAt(), BACKFILL_ACTOR));
            }
        }
        if (!missing.isEmpty()) {
            log.info("Recorded a starting history for {} applications that predate status history", missing.size());
        }
    }
}
