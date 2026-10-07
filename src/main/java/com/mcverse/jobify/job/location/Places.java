package com.mcverse.jobify.job.location;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** What the parser knows about places: countries (from the JDK, plus common aliases) and US states. */
public final class Places {

    private static final Map<String, String> COUNTRY_BY_NAME = new HashMap<>();
    private static final Map<String, String> STATE_BY_NAME = new HashMap<>();

    /** Two-letter codes that are both a country and a US state. Written bare, in capitals, they mean the state. */
    static final Set<String> STATE_WINS = Set.of("CA", "AL", "AR", "AZ", "CO", "GA", "ID", "LA", "MA", "MD", "ME",
            "MN", "MO", "MS", "MT", "NC", "NE", "PA", "SC", "SD", "VA");

    static final Set<String> US_STATES = Set.of("AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "DC", "FL", "GA",
            "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE",
            "NV", "NH", "NJ", "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT",
            "VT", "VA", "WA", "WV", "WI", "WY");

    static {
        for (String code : Locale.getISOCountries()) {
            Locale country = new Locale("", code);
            COUNTRY_BY_NAME.put(key(country.getDisplayCountry(Locale.ENGLISH)), code);
            COUNTRY_BY_NAME.put(key(country.getISO3Country()), code);
        }
        String[][] aliases = {
                {"US", "usa", "u s", "u s a", "america", "united states of america"},
                {"GB", "uk", "u k", "great britain", "britain", "england", "scotland", "wales", "northern ireland"},
                {"AE", "uae"}, {"NL", "holland", "the netherlands", "nederland"},
                {"DE", "deutschland"}, {"ES", "espana"}, {"AT", "osterreich"},
                {"CH", "schweiz", "suisse", "svizzera"}, {"IT", "italia"}, {"CZ", "czech republic"},
                {"TR", "turkey", "turkiye"}, {"KR", "south korea", "korea", "republic of korea"},
                {"RU", "russia"}, {"VN", "vietnam"}, {"CI", "ivory coast"}, {"MM", "burma"},
                {"MK", "macedonia"}, {"CV", "cape verde"}, {"BR", "brasil"}, {"MX", "mexico"},
                {"PL", "polska"}, {"SE", "sverige"}, {"BE", "belgie", "belgique"}, {"LU", "luxemburg"},
                {"HK", "hong kong"}, {"PS", "palestine"}, {"SZ", "swaziland"}, {"TW", "taiwan"}, {"IR", "iran"},
                {"SY", "syria"}, {"LA", "laos"}, {"BO", "bolivia"}, {"VE", "venezuela"}, {"TZ", "tanzania"},
                {"MD", "moldova"}, {"BN", "brunei"}, {"CD", "dr congo", "drc"}, {"CG", "republic of the congo"},
        };
        for (String[] row : aliases) {
            for (int i = 1; i < row.length; i++) {
                COUNTRY_BY_NAME.put(key(row[i]), row[0]);
            }
        }
        String[] states = {"AL Alabama", "AK Alaska", "AZ Arizona", "AR Arkansas", "CA California", "CO Colorado",
                "CT Connecticut", "DE Delaware", "DC District of Columbia", "FL Florida", "GA Georgia", "HI Hawaii",
                "ID Idaho", "IL Illinois", "IN Indiana", "IA Iowa", "KS Kansas", "KY Kentucky", "LA Louisiana",
                "ME Maine", "MD Maryland", "MA Massachusetts", "MI Michigan", "MN Minnesota", "MS Mississippi",
                "MO Missouri", "MT Montana", "NE Nebraska", "NV Nevada", "NH New Hampshire", "NJ New Jersey",
                "NM New Mexico", "NY New York", "NC North Carolina", "ND North Dakota", "OH Ohio", "OK Oklahoma",
                "OR Oregon", "PA Pennsylvania", "RI Rhode Island", "SC South Carolina", "SD South Dakota",
                "TN Tennessee", "TX Texas", "UT Utah", "VT Vermont", "VA Virginia", "WA Washington",
                "WV West Virginia", "WI Wisconsin", "WY Wyoming"};
        for (String s : states) {
            STATE_BY_NAME.put(key(s.substring(3)), s.substring(0, 2));
        }
    }

    private Places() {}

    /** Lower case, no accents, letters and digits separated by single spaces: the form places are compared in. */
    public static String key(String text) {
        if (text == null) {
            return "";
        }
        String s = text.toLowerCase(Locale.ROOT).replace("ß", "ss").replace("ø", "o").replace("æ", "ae")
                .replace("ł", "l").replace("đ", "d").replace("&", " and ");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return s.replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** ISO code of a country written as a name, an alias or a three-letter code; null if it is not one. */
    static String countryByName(String text) {
        String k = key(text);
        return k.length() < 2 ? null : COUNTRY_BY_NAME.get(k);
    }

    private static final Set<String> COUNTRY_CODES = Set.of(Locale.getISOCountries());

    static boolean isCountryCode(String twoLetters) {
        return COUNTRY_CODES.contains(twoLetters);
    }

    /** A US state written as its two-letter code or its full name; null if it is not one. */
    static String stateCode(String text) {
        String trimmed = text.trim();
        if (trimmed.length() == 2 && US_STATES.contains(trimmed.toUpperCase(Locale.ROOT))) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        return STATE_BY_NAME.get(key(text));
    }

    public static String countryName(String code) {
        return new Locale("", code).getDisplayCountry(Locale.ENGLISH);
    }
}
