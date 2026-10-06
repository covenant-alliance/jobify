package com.mcverse.jobify.admin;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditLogTest {

    private final Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final AuditLog audit = new AuditLog();

    @BeforeEach
    void attach() {
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        auditLogger.detachAppender(appender);
    }

    @Test
    void writesWhoDidWhatOnTheAuditLogger() {
        audit.record("admin", "APPROVE_DELETION", "account=bob");
        assertEquals(1, appender.list.size());
        String line = appender.list.get(0).getFormattedMessage();
        assertTrue(line.contains("admin='admin'"));
        assertTrue(line.contains("action=APPROVE_DELETION"));
        assertTrue(line.contains("account=bob"));
    }

    @Test
    void flattensLineBreaksSoInputCannotForgeAnEntry() {
        audit.record("admin", "LIST_USERS", "q='x'\nadmin='root' action=APPROVE_DELETION\r\n");
        String line = appender.list.get(0).getFormattedMessage();
        assertFalse(line.contains("\n"));
        assertFalse(line.contains("\r"));
        assertEquals(1, appender.list.size());
    }
}
