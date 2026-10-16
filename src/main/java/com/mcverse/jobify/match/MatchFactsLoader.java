package com.mcverse.jobify.match;

import com.mcverse.jobify.job.model.JobPost;
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
import java.util.Comparator;

/**
 * Turns entities into {@link MatchFacts} inside a short read-only transaction. It is a separate bean on purpose: the
 * transaction ends before the network call starts, so a slow matching service cannot hold a database connection.
 */
@Service
public class MatchFactsLoader {

    private final SeekerSkillRepository seekerSkills;
    private final SeekerJobPreferencesRepository preferences;

    public MatchFactsLoader(SeekerSkillRepository seekerSkills, SeekerJobPreferencesRepository preferences) {
        this.seekerSkills = seekerSkills;
        this.preferences = preferences;
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
