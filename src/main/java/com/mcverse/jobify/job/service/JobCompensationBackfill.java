package com.mcverse.jobify.job.service;

import com.mcverse.jobify.job.repository.JobRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-off data migration for databases created before the compensation model ({@code rate} + {@code rateType}).
 * Safe to run on every boot: it only touches rows that have no rate yet, and never deletes anything.
 * There is no migration tool yet ({@code ddl-auto=update}), so this stands in for a Flyway script.
 */
@Component
@Order(100)
public class JobCompensationBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JobCompensationBackfill.class);

    private final JobRepo jobRepo;

    public JobCompensationBackfill(JobRepo jobRepo) {
        this.jobRepo = jobRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int updated = jobRepo.backfillCompensation();
        if (updated > 0) {
            log.info("Backfilled rate and rateType (HOURLY) on {} existing job posts", updated);
        }
    }
}
