package com.mcverse.jobify.job.search;

import com.mcverse.jobify.common.model.EmploymentType;
import com.mcverse.jobify.job.model.WorkMode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A validated, normalised job search. All input checking lives here so the rules are in one place and can be tested
 * without a database; every problem is reported as an {@link IllegalArgumentException} with a readable message, which
 * the API answers with 400.
 */
public record JobSearchCriteria(
        String q,
        String location,
        Set<WorkMode> workModes,
        Set<EmploymentType> employmentTypes,
        Double minRate,
        Double maxRate,
        List<String> skills,
        SkillsMatch skillsMatch,
        Availability availability,
        Sort sort,
        int page,
        int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;
    public static final int MAX_TEXT_LENGTH = 100;
    public static final int MAX_SKILLS = 20;

    /** Whether a job needs any or all of the requested skills. */
    public enum SkillsMatch { ANY, ALL }

    /** Which jobs by open or closed state. The default is OPEN, as for a visitor. */
    public enum Availability { OPEN, CLOSED, ALL }

    /** The allowed sort orders (a fixed list, so nothing the caller sends reaches the query as a column name). */
    public enum Sort {
        NEWEST("newest"), OLDEST("oldest"), RATE_DESC("rateDesc"), RATE_ASC("rateAsc"), TITLE("title");

        private final String parameter;

        Sort(String parameter) { this.parameter = parameter; }

        static Sort parse(String value) {
            if (value == null || value.isBlank()) {
                return NEWEST;
            }
            for (Sort sort : values()) {
                if (sort.parameter.equals(value.trim())) {
                    return sort;
                }
            }
            throw new IllegalArgumentException(
                    "sort must be one of: newest, oldest, rateDesc, rateAsc, title.");
        }
    }

    /** Whether a minimum or maximum rate was given; such a search leaves out jobs paid as a fixed contract total. */
    public boolean hasRateFilter() {
        return minRate != null || maxRate != null;
    }

    public static JobSearchCriteria of(String q, String location, List<WorkMode> workModes,
                                       List<EmploymentType> employmentTypes, Double minRate, Double maxRate,
                                       List<String> skills, String skillsMatch, String available, String sort,
                                       Integer page, Integer size) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNumber < 0) {
            throw new IllegalArgumentException("page must be 0 or more.");
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE + ".");
        }
        if (minRate != null && (minRate.isNaN() || minRate < 0)) {
            throw new IllegalArgumentException("minRate must be 0 or more.");
        }
        if (maxRate != null && (maxRate.isNaN() || maxRate < 0)) {
            throw new IllegalArgumentException("maxRate must be 0 or more.");
        }
        if (minRate != null && maxRate != null && minRate > maxRate) {
            throw new IllegalArgumentException("minRate must not be greater than maxRate.");
        }
        return new JobSearchCriteria(
                text("q", q), text("location", location),
                workModes == null ? Set.of() : new LinkedHashSet<>(workModes),
                employmentTypes == null ? Set.of() : new LinkedHashSet<>(employmentTypes),
                minRate, maxRate, skillNames(skills), parseSkillsMatch(skillsMatch),
                parseAvailability(available), Sort.parse(sort), pageNumber, pageSize);
    }

    /** Trims; a blank value means "no filter"; an over-long one is refused rather than silently cut. */
    private static String text(String name, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(name + " must be at most " + MAX_TEXT_LENGTH + " characters.");
        }
        return trimmed;
    }

    private static List<String> skillNames(List<String> skills) {
        if (skills == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String skill : skills) {
            if (skill == null || skill.isBlank()) {
                continue;
            }
            String name = skill.trim();
            if (name.length() > MAX_TEXT_LENGTH) {
                throw new IllegalArgumentException("each skill must be at most " + MAX_TEXT_LENGTH + " characters.");
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (!names.contains(lower)) {
                names.add(lower);
            }
        }
        if (names.size() > MAX_SKILLS) {
            throw new IllegalArgumentException("skills must have at most " + MAX_SKILLS + " entries.");
        }
        return List.copyOf(names);
    }

    private static SkillsMatch parseSkillsMatch(String value) {
        if (value == null || value.isBlank()) {
            return SkillsMatch.ANY;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "ANY" -> SkillsMatch.ANY;
            case "ALL" -> SkillsMatch.ALL;
            default -> throw new IllegalArgumentException("skillsMatch must be ANY or ALL.");
        };
    }

    private static Availability parseAvailability(String value) {
        if (value == null || value.isBlank()) {
            return Availability.OPEN;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> Availability.OPEN;
            case "false" -> Availability.CLOSED;
            case "all" -> Availability.ALL;
            default -> throw new IllegalArgumentException("available must be true, false or all.");
        };
    }
}
