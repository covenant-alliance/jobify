package com.mcverse.jobify.job.model;

/** How the {@code rate} of a job posting is expressed. */
public enum RateType {
    HOURLY,
    MONTHLY,
    YEARLY,
    /** A fixed total for the whole engagement. Not comparable with the others. */
    CONTRACT_TOTAL;

    /** Working hours per month/year used to compare rates, matching the front end's conversion. */
    public static final double HOURS_PER_MONTH = 173.33;
    public static final double HOURS_PER_YEAR = 2080.0;

    /**
     * The rate expressed per year, or {@code null} when it cannot be compared ({@link #CONTRACT_TOTAL}). Used to hand
     * every pay figure to the matching service in one unit.
     */
    public Double toAnnual(double rate) {
        return switch (this) {
            case HOURLY -> rate * HOURS_PER_YEAR;
            case MONTHLY -> rate * 12;
            case YEARLY -> rate;
            case CONTRACT_TOTAL -> null;
        };
    }

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
