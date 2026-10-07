package com.mcverse.jobify.job.service;

import com.mcverse.jobify.job.dto.LocationFacet;
import com.mcverse.jobify.job.location.LocationParser;
import com.mcverse.jobify.job.location.ParsedLocation;
import com.mcverse.jobify.job.location.Places;
import com.mcverse.jobify.job.model.JobPost;
import com.mcverse.jobify.job.model.Location;
import com.mcverse.jobify.job.repository.JobRepo;
import com.mcverse.jobify.job.repository.LocationRepo;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Turns a job's free-text location into a shared place, and lists the places with their job counts. */
@Service
public class LocationService {

    private final LocationRepo locations;
    private final JobRepo jobs;

    public LocationService(LocationRepo locations, JobRepo jobs) {
        this.locations = locations;
        this.jobs = jobs;
    }

    /** The place the text names, created if new; null when the text names no place. */
    @Transactional
    public Location resolve(String text) {
        return LocationParser.parse(text).map(this::findOrCreate).orElse(null);
    }

    private Location findOrCreate(ParsedLocation parsed) {
        String cityKey = parsed.city() == null ? null : Places.key(parsed.city());
        String key = key(cityKey, parsed);
        var exact = locations.findByLookupKey(key);
        if (exact.isPresent()) {
            return exact.get();
        }
        if (cityKey != null) {
            // "Berlin" typed without its country joins the one known "Berlin, Germany", if there is exactly one
            List<Location> fits = locations.findAllByCityKeyAndRemote(cityKey, parsed.remote()).stream()
                    .filter(l -> parsed.countryCode() == null || parsed.countryCode().equals(l.getCountryCode()))
                    .filter(l -> parsed.region() == null || parsed.region().equalsIgnoreCase(l.getRegion()))
                    .toList();
            if (fits.size() == 1) {
                return fits.get(0);
            }
        }
        return locations.save(new Location(key, cityKey, displayName(parsed), parsed.city(), parsed.region(),
                parsed.countryCode(), parsed.remote()));
    }

    private static String key(String cityKey, ParsedLocation p) {
        return Objects.toString(cityKey, "") + "|" + Places.key(Objects.toString(p.region(), "")) + "|"
                + Objects.toString(p.countryCode(), "") + "|" + (p.remote() ? "remote" : "onsite");
    }

    static String displayName(ParsedLocation p) {
        StringBuilder name = new StringBuilder();
        append(name, p.city());
        append(name, p.region());
        append(name, p.countryCode() == null ? null : Places.countryName(p.countryCode()));
        if (p.remote()) {
            return name.isEmpty() ? "Remote" : p.city() == null ? "Remote, " + name : name + " (remote)";
        }
        return name.toString();
    }

    private static void append(StringBuilder name, String part) {
        if (part != null && !part.isBlank()) {
            if (!name.isEmpty()) name.append(", ");
            name.append(part);
        }
    }

    /**
     * Places with job counts, most jobs first. {@code available} null counts every job, otherwise open or closed
     * ones; {@code nameContains} narrows by display name.
     */
    @Transactional(readOnly = true)
    public List<LocationFacet> facets(Boolean available, String nameContains, int limit) {
        String pattern = nameContains == null ? null
                : "%" + nameContains.toLowerCase(java.util.Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                        .replace("_", "\\_") + "%";
        return locations.facets(available, pattern, PageRequest.of(0, limit)).stream().map(row -> {
            Location l = (Location) row[0];
            return new LocationFacet(l.getId(), l.getDisplayName(), l.getCity(), l.getRegion(), l.getCountryCode(),
                    l.getCountryCode() == null ? null : Places.countryName(l.getCountryCode()), l.isRemote(),
                    (Long) row[1]);
        }).toList();
    }

    /** Gives every job from before places existed its place. Safe to run again: it only touches jobs without one. */
    @Transactional
    public int backfill() {
        int filled = 0;
        for (JobPost job : jobs.findAllByLocationRefIsNullAndLocationIsNotNullOrderByPostId()) {
            Location place = resolve(job.getLocation());
            if (place != null) {
                job.setLocationRef(place);
                filled++;
            }
        }
        return filled;
    }
}
