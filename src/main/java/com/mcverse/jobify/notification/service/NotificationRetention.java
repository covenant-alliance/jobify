package com.mcverse.jobify.notification.service;

import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Keeps the notifications table from growing for ever: every night, read notifications older than
 * {@code app.notifications.read-retention-days} (default 90) and any notification older than
 * {@code app.notifications.retention-days} (default 365) are deleted. A value of 0 switches that rule off.
 */
@Component
public class NotificationRetention {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetention.class);

    private final NotificationRepository repository;
    private final AuditLog auditLog;
    private final Clock clock;
    private final int readDays;
    private final int allDays;

    public NotificationRetention(NotificationRepository repository, AuditLog auditLog, Clock clock,
                                 @Value("${app.notifications.read-retention-days:90}") int readDays,
                                 @Value("${app.notifications.retention-days:365}") int allDays) {
        this.repository = repository;
        this.auditLog = auditLog;
        this.clock = clock;
        this.readDays = readDays;
        this.allDays = allDays;
    }

    // @Transactional here too: purge() called from this method would bypass its own proxy and run without a transaction
    @Scheduled(cron = "${app.notifications.purge-cron:0 30 3 * * *}")
    @Transactional
    public void scheduled() {
        purge();
    }

    /** Runs the clean-up now. Returns how many read and how many other notifications were deleted. */
    @Transactional
    public int[] purge() {
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneId.systemDefault()));
        int read = readDays > 0 ? repository.deleteReadBefore(now.minusDays(readDays)) : 0;
        int other = allDays > 0 ? repository.deleteBefore(now.minusDays(allDays)) : 0;
        if (read + other > 0) {
            log.info("Deleted {} read and {} older notifications", read, other);
            auditLog.event("system", "NOTIFICATIONS_PURGED", "read=" + read + " other=" + other);
        }
        return new int[] {read, other};
    }
}
