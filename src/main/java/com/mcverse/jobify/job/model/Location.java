package com.mcverse.jobify.job.model;

import jakarta.persistence.*;

/**
 * A place jobs can be filtered by, learned from the free-text locations employers type. Jobs keep their own text;
 * this is the clean value behind it. Two spellings of one place share a row.
 */
@Entity
@Table(name = "locations")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Identity of the place: city, region, country and remote flag in normalised form. Unique. */
    @Column(nullable = false, unique = true, length = 255)
    private String lookupKey;

    /** The city on its own in normalised form, to find a city that was typed without its country. */
    @Column(length = 255)
    private String cityKey;

    @Column(nullable = false, length = 255)
    private String displayName;

    @Column(length = 255)
    private String city;

    @Column(length = 255)
    private String region;

    @Column(length = 2)
    private String countryCode;

    @Column(nullable = false)
    private boolean remote;

    protected Location() {}

    public Location(String lookupKey, String cityKey, String displayName, String city, String region,
                    String countryCode, boolean remote) {
        this.lookupKey = lookupKey;
        this.cityKey = cityKey;
        this.displayName = displayName;
        this.city = city;
        this.region = region;
        this.countryCode = countryCode;
        this.remote = remote;
    }

    public String getId()          { return id; }
    public String getLookupKey()   { return lookupKey; }
    public String getCityKey()     { return cityKey; }
    public String getDisplayName() { return displayName; }
    public String getCity()        { return city; }
    public String getRegion()      { return region; }
    public String getCountryCode() { return countryCode; }
    public boolean isRemote()      { return remote; }
}
