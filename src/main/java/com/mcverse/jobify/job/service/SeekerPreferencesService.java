package com.mcverse.jobify.job.service;

import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.common.exception.LicenseValidationException;
import com.mcverse.jobify.job.dto.SeekerPreferencesRequest;
import com.mcverse.jobify.job.dto.SeekerPreferencesResponse;
import com.mcverse.jobify.job.location.Places;
import com.mcverse.jobify.job.model.RateType;
import com.mcverse.jobify.job.model.SeekerJobPreferences;
import com.mcverse.jobify.job.repository.SeekerJobPreferencesRepository;
import com.mcverse.jobify.user.model.Seeker;
import com.mcverse.jobify.user.repository.SeekerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** A seeker's own job preferences: read, and replace as a whole. */
@Service
public class SeekerPreferencesService {

    private static final Logger log = LoggerFactory.getLogger(SeekerPreferencesService.class);

    private final SeekerJobPreferencesRepository preferencesRepo;
    private final SeekerRepository seekerRepo;
    private final AuditLog auditLog;

    public SeekerPreferencesService(SeekerJobPreferencesRepository preferencesRepo, SeekerRepository seekerRepo,
                                    AuditLog auditLog) {
        this.preferencesRepo = preferencesRepo;
        this.seekerRepo = seekerRepo;
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public SeekerPreferencesResponse get(String username) {
        Seeker seeker = requireSeeker(username, "read job preferences");
        return preferencesRepo.findById(seeker.getId()).map(this::toResponse).orElseGet(this::empty);
    }

    /** Replaces everything: what the request leaves out becomes empty. */
    @Transactional
    public SeekerPreferencesResponse replace(String username, SeekerPreferencesRequest request) {
        Seeker seeker = requireSeeker(username, "save job preferences");
        validate(request);

        SeekerJobPreferences prefs = preferencesRepo.findById(seeker.getId())
                .orElseGet(() -> new SeekerJobPreferences(seeker.getId()));
        prefs.setPreferredJobTitle(blankToNull(request.preferredJobTitle()));
        prefs.getCountryCodes().clear();
        prefs.getCountryCodes().addAll(normalisedCountries(request.countryCodes()));
        prefs.getWorkModes().clear();
        orEmpty(request.workModes()).forEach(prefs.getWorkModes()::add);
        prefs.getEmploymentTypes().clear();
        orEmpty(request.employmentTypes()).forEach(prefs.getEmploymentTypes()::add);
        prefs.setMinRate(request.minRate());
        prefs.setMaxRate(request.maxRate());
        boolean hasRate = request.minRate() != null || request.maxRate() != null;
        prefs.setRateType(hasRate ? request.rateType() : null);
        prefs.setExperienceLevel(request.experienceLevel());
        prefs.touch();

        log.info("Seeker '{}' saved their job preferences", username);
        return toResponse(preferencesRepo.save(prefs));
    }

    private void validate(SeekerPreferencesRequest request) {
        boolean hasRate = request.minRate() != null || request.maxRate() != null;
        if (hasRate && request.rateType() == null) {
            throw new IllegalArgumentException("rateType is required when minRate or maxRate is given");
        }
        if (request.rateType() == RateType.CONTRACT_TOTAL) {
            throw new IllegalArgumentException("rateType must be HOURLY, MONTHLY or YEARLY for a preference");
        }
        if (request.minRate() != null && request.maxRate() != null && request.minRate() > request.maxRate()) {
            throw new IllegalArgumentException("minRate must not be greater than maxRate");
        }
    }

    private Set<String> normalisedCountries(List<String> codes) {
        Set<String> result = new TreeSet<>();
        for (String code : orEmpty(codes)) {
            String upper = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
            if (!Places.isCountryCode(upper)) {
                throw new IllegalArgumentException("countryCodes contains an unknown country code: "
                        + (code == null ? "null" : code.trim()));
            }
            result.add(upper);
        }
        return result;
    }

    private SeekerPreferencesResponse toResponse(SeekerJobPreferences prefs) {
        return new SeekerPreferencesResponse(
                prefs.getPreferredJobTitle(),
                prefs.getCountryCodes().stream().sorted().toList(),
                prefs.getWorkModes().stream().sorted(Comparator.naturalOrder()).toList(),
                prefs.getEmploymentTypes().stream().sorted(Comparator.naturalOrder()).toList(),
                prefs.getMinRate(), prefs.getMaxRate(), prefs.getRateType(), prefs.getExperienceLevel(),
                prefs.getUpdatedAt());
    }

    private SeekerPreferencesResponse empty() {
        return new SeekerPreferencesResponse(null, List.of(), List.of(), List.of(), null, null, null, null, null);
    }

    /** For other slices that need a seeker's preferences (the matching client, later). */
    @Transactional(readOnly = true)
    public Optional<SeekerJobPreferences> findFor(String seekerId) {
        return preferencesRepo.findById(seekerId);
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private Seeker requireSeeker(String username, String action) {
        return seekerRepo.findByUsername(username).orElseThrow(() -> {
            log.warn("Denied: user '{}' tried to {} without the SEEKER role", username, action);
            auditLog.event(username, "ACCESS_DENIED", "action='" + action + "' reason=not a seeker");
            return new LicenseValidationException("Only job seekers can " + action + ".");
        });
    }
}
