package com.mcverse.jobify.job.location;

/**
 * A free-text job location split into its parts. Any part may be null; a location with only {@code remote} set is
 * plain "Remote". Never produced empty: see {@link LocationParser#parse}.
 */
public record ParsedLocation(String city, String region, String countryCode, boolean remote) {}
