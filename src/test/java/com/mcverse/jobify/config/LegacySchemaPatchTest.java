package com.mcverse.jobify.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A fresh schema uses VARCHAR for enum columns, and an old ENUM column is converted without data loss. */
@SpringBootTest
class LegacySchemaPatchTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private LegacySchemaPatch patch;

    private String employmentTypeColumnType() {
        return jdbc.queryForObject("SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = 'JOB_POSTS' AND COLUMN_NAME = 'EMPLOYMENT_TYPE'", String.class);
    }

    @Test
    void freshSchemaUsesVarcharForEnumColumns() {
        assertEquals("CHARACTER VARYING", employmentTypeColumnType());
    }

    @Test
    void oldEnumColumnIsConvertedAndKeepsItsData() {
        // other test classes share this database and may have stored B2B, which the old enum cannot hold
        jdbc.update("UPDATE job_posts SET employment_type = 'CONTRACT' WHERE employment_type = 'B2B'");
        patch.run(null); // clear the check constraint so the column can be turned back into an old-style ENUM
        Integer before = jdbc.queryForObject(
                "SELECT COUNT(*) FROM job_posts WHERE employment_type = 'FULL_TIME'", Integer.class);
        jdbc.execute("ALTER TABLE job_posts ALTER COLUMN employment_type SET DATA TYPE "
                + "ENUM('CONTRACT','FREELANCE','FULL_TIME','INTERNSHIP','PART_TIME','TEMPORARY')");
        assertEquals("ENUM", employmentTypeColumnType());

        patch.run(null);

        assertEquals("CHARACTER VARYING", employmentTypeColumnType());
        assertEquals(before, jdbc.queryForObject(
                "SELECT COUNT(*) FROM job_posts WHERE employment_type = 'FULL_TIME'", Integer.class));
        jdbc.update("UPDATE job_posts SET employment_type = 'B2B' WHERE post_id = (SELECT MIN(post_id) FROM job_posts)");
    }

    @Test
    void enumValuesAreNotLimitedByAConstraintAfterThePatch() {
        patch.run(null);
        jdbc.update("UPDATE job_posts SET employment_type = 'SOME_FUTURE_TYPE' WHERE post_id = "
                + "(SELECT MIN(post_id) FROM job_posts)");
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM job_posts WHERE employment_type = 'SOME_FUTURE_TYPE'", Integer.class);
        assertEquals(1, n);
        jdbc.update("UPDATE job_posts SET employment_type = 'FULL_TIME' WHERE employment_type = 'SOME_FUTURE_TYPE'");
    }

    @Test
    void runningTwiceChangesNothing() {
        patch.run(null);
        patch.run(null);
        assertEquals("CHARACTER VARYING", employmentTypeColumnType());
    }
}
