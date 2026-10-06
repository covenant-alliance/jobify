package com.mcverse.jobify.job.search;

import com.mcverse.jobify.common.model.EmploymentType;
import com.mcverse.jobify.job.model.WorkMode;
import com.mcverse.jobify.job.search.JobSearchCriteria.Availability;
import com.mcverse.jobify.job.search.JobSearchCriteria.SkillsMatch;
import com.mcverse.jobify.job.search.JobSearchCriteria.Sort;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every input rule of the job search, and its message, with no database. */
class JobSearchCriteriaTest {

    private static JobSearchCriteria none() {
        return JobSearchCriteria.of(null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static String message(Runnable invalid) {
        return assertThrows(IllegalArgumentException.class, invalid::run).getMessage();
    }

    @Test
    void everythingIsOptionalAndTheDefaultsAreOpenJobsNewestFirstTwentyPerPage() {
        JobSearchCriteria c = none();
        assertNull(c.q());
        assertNull(c.location());
        assertTrue(c.workModes().isEmpty());
        assertTrue(c.employmentTypes().isEmpty());
        assertTrue(c.skills().isEmpty());
        assertFalse(c.hasRateFilter());
        assertEquals(SkillsMatch.ANY, c.skillsMatch());
        assertEquals(Availability.OPEN, c.availability());
        assertEquals(Sort.NEWEST, c.sort());
        assertEquals(0, c.page());
        assertEquals(20, c.size());
    }

    @Test
    void textIsTrimmedAndBlankMeansNoFilter() {
        JobSearchCriteria c = JobSearchCriteria.of("  java  ", "   ", null, null, null, null, null, null, null, null,
                null, null);
        assertEquals("java", c.q());
        assertNull(c.location());
    }

    @Test
    void overLongTextIsRefusedNotSilentlyCut() {
        String tooLong = "x".repeat(101);
        assertEquals("q must be at most 100 characters.", message(() -> JobSearchCriteria.of(tooLong, null, null,
                null, null, null, null, null, null, null, null, null)));
        assertEquals("location must be at most 100 characters.", message(() -> JobSearchCriteria.of(null, tooLong,
                null, null, null, null, null, null, null, null, null, null)));
        assertEquals("each skill must be at most 100 characters.", message(() -> JobSearchCriteria.of(null, null,
                null, null, null, null, List.of(tooLong), null, null, null, null, null)));
    }

    @Test
    void pageAndSizeAreChecked() {
        assertEquals("page must be 0 or more.", message(() -> JobSearchCriteria.of(null, null, null, null, null, null,
                null, null, null, null, -1, null)));
        for (int bad : new int[] {0, -5, 51, 1000}) {
            assertEquals("size must be between 1 and 50.", message(() -> JobSearchCriteria.of(null, null, null, null,
                    null, null, null, null, null, null, null, bad)));
        }
        assertEquals(50, JobSearchCriteria.of(null, null, null, null, null, null, null, null, null, null, 3, 50).size());
        assertEquals(1, JobSearchCriteria.of(null, null, null, null, null, null, null, null, null, null, 0, 1).size());
    }

    @Test
    void sortIsAFixedList() {
        for (Sort expected : Sort.values()) {
            String parameter = switch (expected) {
                case NEWEST -> "newest";
                case OLDEST -> "oldest";
                case RATE_DESC -> "rateDesc";
                case RATE_ASC -> "rateAsc";
                case TITLE -> "title";
            };
            assertEquals(expected, JobSearchCriteria.of(null, null, null, null, null, null, null, null, null,
                    parameter, null, null).sort());
        }
        for (String bad : new String[] {"id", "rate", "NEWEST", "title; drop table job_posts", "createdAt desc"}) {
            assertEquals("sort must be one of: newest, oldest, rateDesc, rateAsc, title.", message(() ->
                    JobSearchCriteria.of(null, null, null, null, null, null, null, null, null, bad, null, null)));
        }
    }

    @Test
    void rateBoundsMustBeSensible() {
        assertEquals("minRate must be 0 or more.", message(() -> JobSearchCriteria.of(null, null, null, null, -1.0,
                null, null, null, null, null, null, null)));
        assertEquals("maxRate must be 0 or more.", message(() -> JobSearchCriteria.of(null, null, null, null, null,
                -0.5, null, null, null, null, null, null)));
        assertEquals("minRate must not be greater than maxRate.", message(() -> JobSearchCriteria.of(null, null, null,
                null, 90.0, 40.0, null, null, null, null, null, null)));
        assertEquals("minRate must be 0 or more.", message(() -> JobSearchCriteria.of(null, null, null, null,
                Double.NaN, null, null, null, null, null, null, null)));
        JobSearchCriteria equal = JobSearchCriteria.of(null, null, null, null, 50.0, 50.0, null, null, null, null,
                null, null);
        assertTrue(equal.hasRateFilter());
        assertTrue(JobSearchCriteria.of(null, null, null, null, null, 50.0, null, null, null, null, null, null)
                .hasRateFilter());
    }

    @Test
    void skillNamesAreTrimmedLowerCasedDeduplicatedAndBlanksDropped() {
        JobSearchCriteria c = JobSearchCriteria.of(null, null, null, null, null, null,
                Arrays.asList(" Java ", "java", "SPRING", "", "  ", null), null, null, null, null, null);
        assertEquals(List.of("java", "spring"), c.skills());
    }

    @Test
    void atMostTwentySkills() {
        List<String> many = java.util.stream.IntStream.range(0, 21).mapToObj(i -> "skill" + i).toList();
        assertEquals("skills must have at most 20 entries.", message(() -> JobSearchCriteria.of(null, null, null, null,
                null, null, many, null, null, null, null, null)));
        assertEquals(20, JobSearchCriteria.of(null, null, null, null, null, null, many.subList(0, 20), null, null,
                null, null, null).skills().size());
    }

    @Test
    void skillsMatchAndAvailableAcceptOnlyTheirValues() {
        assertEquals(SkillsMatch.ALL, JobSearchCriteria.of(null, null, null, null, null, null, null, "all", null, null,
                null, null).skillsMatch());
        assertEquals(SkillsMatch.ANY, JobSearchCriteria.of(null, null, null, null, null, null, null, "ANY", null, null,
                null, null).skillsMatch());
        assertEquals("skillsMatch must be ANY or ALL.", message(() -> JobSearchCriteria.of(null, null, null, null, null,
                null, null, "SOME", null, null, null, null)));

        assertEquals(Availability.OPEN, JobSearchCriteria.of(null, null, null, null, null, null, null, null, "true",
                null, null, null).availability());
        assertEquals(Availability.CLOSED, JobSearchCriteria.of(null, null, null, null, null, null, null, null, "false",
                null, null, null).availability());
        assertEquals(Availability.ALL, JobSearchCriteria.of(null, null, null, null, null, null, null, null, "ALL", null,
                null, null).availability());
        assertEquals("available must be true, false or all.", message(() -> JobSearchCriteria.of(null, null, null, null,
                null, null, null, null, "maybe", null, null, null)));
    }

    @Test
    void repeatedEnumFiltersAreKeptAsSets() {
        JobSearchCriteria c = JobSearchCriteria.of(null, null, List.of(WorkMode.REMOTE, WorkMode.HYBRID, WorkMode.REMOTE),
                List.of(EmploymentType.B2B), null, null, null, null, null, null, null, null);
        assertEquals(Set.of(WorkMode.REMOTE, WorkMode.HYBRID), c.workModes());
        assertEquals(Set.of(EmploymentType.B2B), c.employmentTypes());
    }
}
