package com.mcverse.jobify.match;

import com.mcverse.jobify.application.model.Application;
import com.mcverse.jobify.application.model.ApplicationStatus;
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
import com.mcverse.jobify.user.model.SeekerSkill;
import com.mcverse.jobify.user.repository.SeekerSkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

    /** An application, who applied, and what the matching service is told about them (no names: those stay here). */
    public record ApplicantWithFacts(String applicationId, ApplicationStatus status, LocalDateTime appliedAt,
                                     String seekerId, String username, String name, String lastName, boolean hasCv,
                                     SeekerFacts facts) {}

    /** A seeker's username (to notify them) next to what the matching service is told about them. */
    public record SeekerWithFacts(String username, SeekerFacts facts) {}

    private final MatchSeekerRepository seekerIds;
    private final com.mcverse.jobify.job.repository.JobRepo jobs;
    private final MatchApplicantRepository applicants;
    private final SeekerSkillRepository seekerSkills;
    private final SeekerJobPreferencesRepository preferences;
    private final MatchCandidateRepository candidates;
    private final JobResponseMapper mapper;

    public MatchFactsLoader(MatchApplicantRepository applicants, SeekerSkillRepository seekerSkills,
                            SeekerJobPreferencesRepository preferences, MatchCandidateRepository candidates,
                            JobResponseMapper mapper, MatchSeekerRepository seekerIds,
                            com.mcverse.jobify.job.repository.JobRepo jobs) {
        this.seekerIds = seekerIds;
        this.jobs = jobs;
        this.applicants = applicants;
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
        var skills = seekerSkills.findBySeekerId(seeker.getId()).stream().map(MatchFactsLoader::toFact).toList();
        PreferenceFacts prefs = preferences.findById(seeker.getId()).map(MatchFactsLoader::toFacts).orElse(null);
        return new SeekerFacts(seeker.getId(), skills, prefs);
    }

    /** The job as the matching service sees it, only while it is open (a closed or deleted job is nobody's match). */
    @Transactional(readOnly = true)
    public Optional<JobFacts> openJob(int jobId) {
        return jobs.findById(jobId).filter(JobPost::isAvailable).map(this::job);
    }

    /** Seekers worth scoring for a new job (see {@link MatchSeekerRepository#interestedIn}), with their facts. */
    @Transactional(readOnly = true)
    public List<SeekerWithFacts> seekersInterestedIn(JobFacts job, int limit) {
        List<String> skillIds = job.requiredSkills().stream().map(SkillRef::id).toList();
        if (skillIds.isEmpty()) {
            return List.of();
        }
        boolean remote = job.workMode() == com.mcverse.jobify.job.model.WorkMode.REMOTE;
        String country = job.countryCode() == null ? "" : job.countryCode();
        List<String> ids = seekerIds.interestedIn(job.jobId(), skillIds, remote, country,
                org.springframework.data.domain.PageRequest.of(0, limit));
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, List<SeekerSkill>> skillsBySeeker = new HashMap<>();
        applicants.skillsOf(ids).forEach(s -> skillsBySeeker.computeIfAbsent(s.getSeeker().getId(), k -> new java.util.ArrayList<>()).add(s));
        Map<String, PreferenceFacts> prefsBySeeker = new HashMap<>();
        preferences.findAllById(ids).forEach(p -> prefsBySeeker.put(p.getSeekerId(), toFacts(p)));
        return seekerIds.findAllByIds(ids).stream().map(seeker -> new SeekerWithFacts(seeker.getUsername(),
                new SeekerFacts(seeker.getId(),
                        skillsBySeeker.getOrDefault(seeker.getId(), List.of()).stream().map(MatchFactsLoader::toFact).toList(),
                        prefsBySeeker.get(seeker.getId())))).toList();
    }

    /** The newest {@code limit} live applications of a job, each with the facts about the applicant. Three queries in all. */
    @Transactional(readOnly = true)
    public List<ApplicantWithFacts> applicants(int jobId, int limit) {
        List<Application> applications = applicants.activeForJob(jobId, org.springframework.data.domain.PageRequest.of(0, limit));
        if (applications.isEmpty()) {
            return List.of();
        }
        List<String> seekerIds = applications.stream().map(a -> a.getSeeker().getId()).toList();
        Map<String, List<SeekerSkill>> skillsBySeeker = new HashMap<>();
        applicants.skillsOf(seekerIds).forEach(s -> skillsBySeeker.computeIfAbsent(s.getSeeker().getId(), k -> new java.util.ArrayList<>()).add(s));
        Map<String, PreferenceFacts> prefsBySeeker = new HashMap<>();
        preferences.findAllById(seekerIds).forEach(p -> prefsBySeeker.put(p.getSeekerId(), toFacts(p)));

        return applications.stream().map(a -> {
            Seeker seeker = a.getSeeker();
            var skills = skillsBySeeker.getOrDefault(seeker.getId(), List.of()).stream().map(MatchFactsLoader::toFact).toList();
            return new ApplicantWithFacts(a.getId(), a.getStatus(), a.getCreatedAt(), seeker.getId(), seeker.getUsername(),
                    seeker.getName(), seeker.getLastName(), seeker.getCv() != null,
                    new SeekerFacts(seeker.getId(), skills, prefsBySeeker.get(seeker.getId())));
        }).toList();
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

    private static SeekerSkillFact toFact(SeekerSkill s) {
        return new SeekerSkillFact(s.getSkill().getId(), s.getProficiencyLevel(),
                s.getYearsOfExperience() == null ? 0 : s.getYearsOfExperience());
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
