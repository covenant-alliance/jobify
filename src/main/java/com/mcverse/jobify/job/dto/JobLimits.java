package com.mcverse.jobify.job.dto;

/** Input limits for job postings. Constants so they can be used in annotations and documented in one place. */
public final class JobLimits {
    public static final int TITLE_MAX = 150;
    /** Applies to the submitted HTML, before sanitizing. */
    public static final int DESCRIPTION_MAX = 20_000;
    public static final int LOCATION_MAX = 255;
    /** Upper sanity bound for a rate, as a string because annotations need constants. */
    public static final String RATE_MAX = "1000000000";
    public static final int SKILLS_MAX = 30;
    public static final int SKILL_NAME_MAX = 100;

    public static final int BENEFITS_MAX = 15;
    public static final int BENEFIT_MAX = 80;
    public static final int IMAGES_MAX = 6;
    public static final long IMAGE_MAX_BYTES = 2L * 1024 * 1024;

    private JobLimits() {}
}
