package com.mcverse.jobify.job.location;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationParserTest {

    private static void check(String text, String city, String region, String country, boolean remote) {
        ParsedLocation p = LocationParser.parse(text).orElseThrow(() -> new AssertionError("no place in: " + text));
        assertEquals(new ParsedLocation(city, region, country, remote), p, "for: " + text);
    }

    @Test
    void citiesWithCountriesInAnyWritingAndCase() {
        check("Berlin, DE", "Berlin", null, "DE", false);
        check("berlin, germany", "Berlin", null, "DE", false);
        check("BERLIN,GERMANY", "Berlin", null, "DE", false);
        check("München, Deutschland", "München", null, "DE", false);
        check("Paris - France", "Paris", null, "FR", false);
        check("Tokyo, JPN", "Tokyo", null, "JP", false);
        check("toronto, ca", "Toronto", null, "CA", false);
        check("Dublin | Ireland", "Dublin", null, "IE", false);
        check("London, UK", "London", null, "GB", false);
        check("Manchester, England", "Manchester", null, "GB", false);
        check("Berlin, Germany (hybrid)", "Berlin", null, "DE", false);
    }

    @Test
    void aCityAloneKeepsNoCountry() {
        check("BERLIN", "Berlin", null, null, false);
        check("frankfurt am main", "Frankfurt am Main", null, null, false);
        check("Frankfurt am Main", "Frankfurt am Main", null, null, false);
        check("Baden-Baden", "Baden-Baden", null, null, false);
        check("saint-denis", "Saint-Denis", null, null, false);
    }

    @Test
    void aCountryAloneIsACountry() {
        check("Germany", null, null, "DE", false);
        check("United States", null, null, "US", false);
        check("Georgia", null, null, "GE", false);
        check("England", null, null, "GB", false);
    }

    @Test
    void usStatesGiveTheCountry() {
        check("Austin, TX", "Austin", "TX", "US", false);
        check("Austin, Texas", "Austin", "TX", "US", false);
        check("Austin, TX, USA", "Austin", "TX", "US", false);
        check("new york, ny", "New York", "NY", "US", false);
        check("New York", "New York", null, null, false);
        check("Los Angeles, CA", "Los Angeles", "CA", "US", false);
        check("Atlanta, Georgia", "Atlanta", "GA", "US", false);
        check("Washington, DC", "Washington", "DC", "US", false);
    }

    @Test
    void otherRegionsAreKept() {
        check("Potsdam, Brandenburg, Germany", "Potsdam", "Brandenburg", "DE", false);
    }

    @Test
    void remoteIsAFlagInEveryWriting() {
        check("Remote", null, null, null, true);
        check("remote", null, null, null, true);
        check("Fully Remote", null, null, null, true);
        check("Worldwide", null, null, null, true);
        check("Anywhere", null, null, null, true);
        check("Remote — US", null, null, "US", true);
        check("Remote, USA", null, null, "US", true);
        check("Remote (US)", null, null, "US", true);
        check("Remote US", null, null, "US", true);
        check("US Remote", null, null, "US", true);
        check("Remote-Germany", null, null, "DE", true);
        check("Zürich, Switzerland / Remote", "Zürich", null, "CH", true);
        check("Berlin remote", "Berlin", null, null, true);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "hybrid", "On-site", "---", ",,,", "()"})
    void noPlaceMeansNothing(String text) {
        assertTrue(LocationParser.parse(text).isEmpty(), "for: " + text);
    }

    @Test
    void extraDetailIsDroppedNotFatal() {
        check("Berlin, Mitte, Kreuzberg, Germany", "Berlin", "Mitte", "DE", false);
    }
}
