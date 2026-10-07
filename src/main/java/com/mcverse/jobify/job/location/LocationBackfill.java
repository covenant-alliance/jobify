package com.mcverse.jobify.job.location;

import com.mcverse.jobify.job.service.LocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** At startup, resolves the place of jobs that predate places (and of jobs whose location was never resolved). */
@Component
@Order(130)
public class LocationBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocationBackfill.class);

    private final LocationService locations;

    public LocationBackfill(LocationService locations) {
        this.locations = locations;
    }

    @Override
    public void run(ApplicationArguments args) {
        int filled = locations.backfill();
        if (filled > 0) {
            log.info("Resolved the place of {} existing jobs", filled);
        }
    }
}
