package com.mcverse.jobify.job.location;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Splits a free-text job location ("Berlin, DE", "Remote — US", "Austin, Texas (hybrid)") into city, region, country
 * and a remote flag. Pure and deterministic. It knows countries (names, aliases such as USA/UK/Deutschland, ISO codes)
 * and US states; every other word is taken as a city (first) or region (second). It cannot know that a city exists.
 */
public final class LocationParser {

    private static final Pattern SEPARATORS = Pattern.compile("\\s*(?:,|;|\\||/|·|[–—]|\\s-\\s)\\s*");
    private static final Set<String> REMOTE_WORDS = Set.of("remote", "remotely", "work from home", "wfh", "anywhere",
            "worldwide", "global", "fully remote", "remote only", "remote first", "100 remote");
    private static final Set<String> NOT_A_PLACE = Set.of("hybrid", "onsite", "on site", "in office", "office");
    private static final Set<String> SMALL_WORDS = Set.of("am", "an", "der", "de", "la", "le", "du", "del", "di", "of",
            "upon", "on", "the", "von", "van", "den", "des", "sur", "les");

    private LocationParser() {}

    public static Optional<ParsedLocation> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        boolean remote = false;
        String country = null;
        String region = null;
        String city = null;

        for (String raw : SEPARATORS.split(text.replace('(', ',').replace(')', ',').trim())) {
            String segment = raw.trim();
            if (segment.isEmpty()) {
                continue;
            }
            String k = Places.key(segment);
            if (k.isEmpty() || NOT_A_PLACE.contains(k)) {
                continue;
            }
            if (REMOTE_WORDS.contains(k)) {
                remote = true;
                continue;
            }
            // "Remote US", "US Remote", "Berlin remote": the word is a flag, the rest is still a place
            String stripped = stripRemoteWord(segment);
            if (stripped != null) {
                remote = true;
                segment = stripped;
                k = Places.key(segment);
                if (k.isEmpty()) {
                    continue;
                }
            }

            String code = countryOf(segment, k, country);
            String state = Places.stateCode(segment);
            if (code != null && state != null && segment.length() > 2 && city != null) {
                code = null; // "Atlanta, Georgia": after a city, a name that is both is the US state
            }
            if (code != null && country == null) {
                country = code;
            } else if (state != null && region == null && (city != null || country != null)) {
                region = state;
            } else if (city == null) {
                city = segment;
            } else if (region == null) {
                region = state != null ? state : segment;
            }
            // anything further is detail we do not keep
        }
        if (country == null && region != null && Places.US_STATES.contains(region)) {
            country = "US";
        }
        if (city == null && region == null && country == null && !remote) {
            return Optional.empty();
        }
        return Optional.of(new ParsedLocation(city == null ? null : prettify(city),
                region == null ? null : (Places.US_STATES.contains(region) ? region : prettify(region)),
                country, remote));
    }

    private static String countryOf(String segment, String key, String countrySoFar) {
        if (segment.length() == 2) {
            String upper = segment.toUpperCase(Locale.ROOT);
            // a bare capital code that is both a country and a US state ("CA", "PA") is read as the state
            if (segment.equals(upper) && Places.STATE_WINS.contains(upper)) {
                return null;
            }
            if (Places.isCountryCode(upper)) {
                return countrySoFar == null ? upper : null;
            }
            return Places.countryByName(key); // "UK" is not an ISO code but everyone writes it
        }
        return Places.countryByName(key);
    }

    /** The segment without a leading or trailing "remote", or null if it has none. */
    private static String stripRemoteWord(String segment) {
        String lower = segment.toLowerCase(Locale.ROOT);
        if (lower.startsWith("remote ") || lower.startsWith("remote-")) {
            return segment.substring(7).trim();
        }
        if (lower.endsWith(" remote")) {
            return segment.substring(0, segment.length() - 7).trim();
        }
        return null;
    }

    /** A name typed all in lower or upper case becomes Title Case; a mixed-case name is left as typed. */
    static String prettify(String name) {
        String trimmed = name.trim().replaceAll("\\s+", " ");
        if (!trimmed.equals(trimmed.toLowerCase(Locale.ROOT)) && !trimmed.equals(trimmed.toUpperCase(Locale.ROOT))) {
            return trimmed;
        }
        List<String> words = new ArrayList<>();
        int index = 0;
        for (String word : trimmed.toLowerCase(Locale.ROOT).split(" ")) {
            words.add(index++ > 0 && SMALL_WORDS.contains(word) ? word : capitalise(word));
        }
        return String.join(" ", words);
    }

    private static String capitalise(String word) {
        StringBuilder out = new StringBuilder(word.length());
        boolean start = true;
        for (char c : word.toCharArray()) {
            out.append(start ? Character.toTitleCase(c) : c);
            start = c == '-' || c == '\'' || c == '.';
        }
        return out.toString();
    }
}
