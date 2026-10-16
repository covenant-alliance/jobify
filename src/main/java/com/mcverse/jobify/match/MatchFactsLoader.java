package com.mcverse.jobify.match;

import com.mcverse.jobify.job.dto.JobPostResponse;
import com.mcverse.jobify.job.model.JobPost;
import com.mcverse.jobify.job.service.JobResponseMapper;
import com.mcverse.jobify.job.model.RateType;
import com.mcverse.jobify.job.model.SeekerJobPreferences;
import com.mcverse.jobify.job.repository.SeekerJobPreferencesRepository;
import com.mcverse.jobify.match.MatchFacts.JobFacts;
import com.mcverse.jobify.match.MatchFacts.PreferenceFacts;
import com.mcverse.jobify.match.MatchFacts.SeekerFacts;
import com.mcverse.jobify.match.MatchFacts.SeekerSkillFact;
import com.mcverse.jobify.match.MatchFacts.SkillRef;
import com.mcverse.jobify.user.model.Seeker;
import com.mcverse.jobify.user.repository.SeekerSkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Turns entities into {@link MatchFacts} inside a short read-only transaction. It is a separate bean on purpose: the
 * transaction ends before the network call starts, so a slow matching service cannot hold a database connection.
 */
@Service
public class MatchFactsLoader {

    /** A job as the matching service sees it, next to how the API shows it. */
    public record JobWithFacts(JobFacts facts, JobPostResponse response) {}

    private final SeekerSkillRepository seekerSkills;
    private final SeekerJobPreferencesRepository preferences;
    private final MatchCandidateRepository candidates;
    private final JobResponseMapper mapper;

    public MatchFactsLoader(SeekerSkillRepository seekerSkills, SeekerJobPreferencesRepository preferences,
                            MatchCandidateRepository candidates, JobResponseMapper mapper) {
        this.seekerSkills = seekerSkills;
        this.preferences = preferences;
        this.candidates = candidates;
        this.mapper = mapper;
    }

    /** Preferred countries of the seeker, or an empty list when none are saved (then no country filter applies). */
    @Transactional(readOnly = true)
    public List<String> preferredCountries(Seeker seeker) {
        return preferences.findById(seeker.getId()).map(p -> p.getCountryCodes().stream().sorted().toList())
                .orElse(List.of());
    }

    /** Ids of the jobs worth ranking for this seeker, newest first, at most {@code limit}. */
    @Transactional(readOnly = true)
    public List<Integer> narrow(String username, List<String> countries, int limit) {
        boolean anyCountry = countries.isEmpty();
        return candidates.narrowForSeeker(username, anyCountry, anyCountry ? List.of("") : countries,
                org.springframework.data.domain.PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<JobWithFacts> jobs(Collection<Integer> ids) {
        return candidates.findAllByPostIdIn(ids).stream()
                .map(job -> new JobWithFacts(job(job), mapper.toResponse(job)))
                .toList();
    }

    @Transactional(readOnly = true)
    public SeekerFacts seeker(Seeker seeker) {
        var skills = seekerSkills.findBySeekerId(seeker.getId()).stream()
                .map(s -> new SeekerSkillFact(s.getSkill().getId(), s.getProficiencyLevel(),
                        s.getYearsOfExperience() == null ? 0 : s.getYearsOfExperience()))
                .toList();
        PreferenceFacts prefs = preferences.findById(seeker.getId()).map(MatchFactsLoader::toFacts).orElse(null);
        return new SeekerFacts(seeker.getId(), skills, prefs);
    }

    @Transactional(readOnly = true)
    public JobFacts job(JobPost job) {
        var skills = job.getRequiredSkills().stream()
                .map(s -> new SkillRef(s.getId(), s.getName()))
                .sorted(Comparator.comparing(SkillRef::name))
                .toList();
        String country = job.getLocationRef() == null ? null : job.getLocationRef().getCountryCode();
        Double annual = job.getRateType() == null || job.getRate() <= 0 ? null : job.getRateType().toAnnual(job.getRate());
        long posted = job.getCreatedAt() == null ? 0 : job.getCreatedAt().toEpochSecond(ZoneOffset.UTC);
        return new JobFacts(job.getPostId(), job.getJobTitle(), skills, country, job.getWorkMode(),
                job.getEmploymentType(), annual, job.getExperienceLevel(), posted);
    }

    private static PreferenceFacts toFacts(SeekerJobPreferences p) {
        RateType unit = p.getRateType();
        return new PreferenceFacts(
                p.getCountryCodes().stream().sorted().toList(),
                p.getWorkModes().stream().sorted().toList(),
                p.getEmploymentTypes().stream().sorted().toList(),
                annual(unit, p.getMinRate()), annual(unit, p.getMaxRate()), p.getExperienceLevel());
    }

    private static Double annual(RateType unit, Double rate) {
        return unit == null || rate == null ? null : unit.toAnnual(rate);
    }
}
