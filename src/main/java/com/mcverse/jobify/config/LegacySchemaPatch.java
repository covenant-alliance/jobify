package com.mcverse.jobify.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Stand-in for a migration tool (planned: Flyway, see backlog T3) on databases created by older builds.
 * Older builds let Hibernate create enum columns as H2 {@code ENUM(...)} types with a fixed value list, which
 * {@code ddl-auto=update} never widens, so a new enum constant such as {@code B2B} is rejected on insert.
 * Hibernate also adds a {@code CHECK (col IN (...))} constraint for enum columns, with the same problem.
 * This turns those columns into plain {@code VARCHAR} and drops those checks, so adding an enum constant never
 * needs a schema change (the values are still validated by Jackson and Hibernate). It changes no data, only acts
 * on columns that still have an ENUM type or a check constraint, and is a no-op once done.
 * It also drops {@code job_posts.job_rating}, which the entity no longer maps (it carried only zeros; on PostgreSQL the
 * same change is migration V2).
 *
 * <p>And it retires {@code job_posts.hourly_rate} (on PostgreSQL: migration V4). Pay is stored as {@code rate} +
 * {@code rate_type}; the old column was only a derived copy. Before dropping it, any row that has no rate yet (a very
 * old row) gets its old hourly rate as {@code rate} with type HOURLY, so no job loses its price, and then both
 * columns become NOT NULL if no row is missing a value.
 */
@ConditionalOnProperty(name = "app.legacy-patches.enabled", havingValue = "true", matchIfMissing = true)
@Component
@Order(0)
public class LegacySchemaPatch implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacySchemaPatch.class);

    private record Column(String table, String column) {}

    private static final List<Column> ENUM_COLUMNS = List.of(
            new Column("JOB_POSTS", "EMPLOYMENT_TYPE"),
            new Column("JOB_POSTS", "WORK_MODE"),
            new Column("JOB_POSTS", "RATE_TYPE"));

    /** Columns the entities no longer map. Old H2 files still have them, some as NOT NULL, which would reject inserts. */
    private static final List<Column> REMOVED_COLUMNS = List.of(new Column("JOB_POSTS", "JOB_RATING"));

    private final JdbcTemplate jdbc;

    public LegacySchemaPatch(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Column c : ENUM_COLUMNS) {
            try {
                dropCheckConstraints(c);
                if (isEnumColumn(c)) {
                    jdbc.execute("ALTER TABLE " + c.table() + " ALTER COLUMN " + c.column()
                            + " SET DATA TYPE VARCHAR(255)");
                    log.info("Converted {}.{} from a database enum to VARCHAR", c.table(), c.column());
                }
            } catch (RuntimeException e) {
                // Not H2, or the table does not exist yet: nothing to patch.
                log.debug("Skipped schema patch for {}.{}: {}", c.table(), c.column(), e.getMessage());
            }
        }
        retireHourlyRate();
        for (Column c : REMOVED_COLUMNS) {
            try {
                if (columnExists(c)) {
                    jdbc.execute("ALTER TABLE " + c.table() + " DROP COLUMN " + c.column());
                    log.info("Dropped unused column {}.{}", c.table(), c.column());
                }
            } catch (RuntimeException e) {
                log.debug("Skipped dropping {}.{}: {}", c.table(), c.column(), e.getMessage());
            }
        }
    }

    /** Copies the old hourly rate into rate/rate_type where those are missing, tightens them, then drops hourly_rate. */
    private void retireHourlyRate() {
        Column hourly = new Column("JOB_POSTS", "HOURLY_RATE");
        try {
            if (!columnExists(hourly)) {
                return;
            }
            int copied = jdbc.update("UPDATE JOB_POSTS SET RATE = HOURLY_RATE, RATE_TYPE = 'HOURLY' "
                    + "WHERE RATE IS NULL OR RATE_TYPE IS NULL");
            Integer missing = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM JOB_POSTS WHERE RATE IS NULL OR RATE_TYPE IS NULL", Integer.class);
            if (missing != null && missing == 0) {
                jdbc.execute("ALTER TABLE JOB_POSTS ALTER COLUMN RATE SET NOT NULL");
                jdbc.execute("ALTER TABLE JOB_POSTS ALTER COLUMN RATE_TYPE SET NOT NULL");
            }
            jdbc.execute("ALTER TABLE JOB_POSTS DROP COLUMN HOURLY_RATE");
            log.info("Dropped JOB_POSTS.HOURLY_RATE (pay now lives in RATE and RATE_TYPE); copied it into {} old rows",
                    copied);
        } catch (RuntimeException e) {
            // not H2, or the table does not exist yet: nothing to patch. The column is kept if the copy failed.
            log.debug("Skipped retiring JOB_POSTS.HOURLY_RATE: {}", e.getMessage());
        }
    }

    private boolean columnExists(Column c) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = ? AND UPPER(COLUMN_NAME) = ?",
                Integer.class, c.table(), c.column());
        return count != null && count > 0;
    }

    private void dropCheckConstraints(Column c) {
        List<String> names = jdbc.queryForList(
                "SELECT tc.CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc "
                        + "JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc "
                        + "ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME "
                        + "WHERE UPPER(tc.TABLE_NAME) = ? AND tc.CONSTRAINT_TYPE = 'CHECK' "
                        + "AND UPPER(cc.CHECK_CLAUSE) LIKE ?",
                String.class, c.table(), "%\"" + c.column() + "\"%");
        for (String name : names) {
            jdbc.execute("ALTER TABLE " + c.table() + " DROP CONSTRAINT \"" + name + "\"");
            log.info("Dropped check constraint {} on {}.{}", name, c.table(), c.column());
        }
    }

    private boolean isEnumColumn(Column c) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE UPPER(TABLE_NAME) = ? AND UPPER(COLUMN_NAME) = ? AND DATA_TYPE = 'ENUM'",
                Integer.class, c.table(), c.column());
        return count != null && count > 0;
    }
}
