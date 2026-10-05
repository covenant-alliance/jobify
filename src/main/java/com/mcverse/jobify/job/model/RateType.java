package com.mcverse.jobify.job.model;

/** How the {@code rate} of a job posting is expressed. */
public enum RateType {
    HOURLY,
    MONTHLY,
    YEARLY,
    /** A fixed total for the whole engagement. Not comparable with the others. */
    CONTRACT_TOTAL;

    /** Working hours per month/year used to compare rates, matching the front end's conversion. */
    private static final double HOURS_PER_MONTH = 173.33;
    private static final double HOURS_PER_YEAR = 2080.0;

    /**
     * The rate expressed per hour, rounded to cents, or {@code 0} when it cannot be compared
     * ({@link #CONTRACT_TOTAL}).
     */
    public double toHourly(double rate) {
        double hourly = switch (this) {
            case HOURLY -> rate;
            case MONTHLY -> rate / HOURS_PER_MONTH;
            case YEARLY -> rate / HOURS_PER_YEAR;
            case CONTRACT_TOTAL -> 0.0;
        };
        return Math.round(hourly * 100.0) / 100.0;
    }
}
