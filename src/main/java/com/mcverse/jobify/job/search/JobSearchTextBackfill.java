package com.mcverse.jobify.job.search;

import com.mcverse.jobify.job.model.JobPost;
import com.mcverse.jobify.job.repository.JobRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Fills the plain-text description of jobs that were saved before that column existed, so search can find their
 * words. Safe on every boot: it touches only jobs whose plain text is still missing, and changes nothing else.
 */
@Component
@Order(120)
public class JobSearchTextBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JobSearchTextBackfill.class);

    private final JobRepo jobRepo;

    public JobSearchTextBackfill(JobRepo jobRepo) {
        this.jobRepo = jobRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<JobPost> missing = jobRepo.findAllByDescriptionTextIsNull();
        missing.forEach(JobPost::refreshDescriptionText);
        if (!missing.isEmpty()) {
            jobRepo.saveAll(missing);
            log.info("Filled the plain-text description of {} existing jobs for search", missing.size());
        }
    }
}
