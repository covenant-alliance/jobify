package com.mcverse.jobify.match;

import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.common.exception.LicenseValidationException;
import com.mcverse.jobify.common.exception.ResourceNotFoundException;
import com.mcverse.jobify.job.model.JobPost;
import com.mcverse.jobify.job.repository.JobRepo;
import com.mcverse.jobify.match.MatchFacts.JobFacts;
import com.mcverse.jobify.common.response.PagedResponse;
import com.mcverse.jobify.match.MatchFacts.MatchResult;
import com.mcverse.jobify.match.MatchFacts.RankedCandidate;
import com.mcverse.jobify.match.MatchFacts.RankedJob;
import com.mcverse.jobify.match.MatchFactsLoader.ApplicantWithFacts;
import com.mcverse.jobify.match.dto.CandidateResponse;
import com.mcverse.jobify.match.MatchFactsLoader.JobWithFacts;
import com.mcverse.jobify.match.dto.RecommendedJobResponse;
import com.mcverse.jobify.match.MatchFacts.SeekerFacts;
import com.mcverse.jobify.match.MatchFacts.SkillRef;
import com.mcverse.jobify.match.dto.JobMatchResponse;
import com.mcverse.jobify.user.model.Seeker;
import com.mcverse.jobify.user.repository.SeekerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Decides who may ask, gathers the facts, asks the engine and shapes the answer. Deliberately not transactional: the
 * facts are loaded in short transactions by {@link MatchFactsLoader}, then the (remote) call runs without one.
 */
@Service
public class MatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);

    private final SeekerRepository seekerRepo;
    private final JobRepo jobRepo;
    private final MatchFactsLoader facts;
    private final MatchEngine engine;
    private final AuditLog auditLog;
    private final MatchProperties properties;
    private final MatchJobAccess jobAccess;

    public static final int MAX_PAGE_SIZE = 50;

    public MatchService(SeekerRepository seekerRepo, JobRepo jobRepo, MatchFactsLoader facts, MatchEngine engine,
                        AuditLog auditLog, MatchProperties properties, MatchJobAccess jobAccess) {
        this.properties = properties;
        this.jobAccess = jobAccess;
        this.seekerRepo = seekerRepo;
        this.jobRepo = jobRepo;
        this.facts = facts;
        this.engine = engine;
        this.auditLog = auditLog;
    }

    /** The signed-in seeker's match with one open job. */
    public JobMatchResponse matchFor(String username, int jobId) {
        Seeker seeker = requireSeeker(username, "see match scores");
        JobPost job = jobRepo.findById(jobId).filter(JobPost::isAvailable)
                .orElseThrow(() -> new ResourceNotFoundException("JobPost", Integer.toString(jobId)));

        SeekerFacts seekerFacts = facts.seeker(seeker);
        JobFacts jobFacts = facts.job(job);

        MatchResult result = engine.score(seekerFacts, jobFacts);
        return toResponse(jobFacts, result);
    }

    /**
     * A page of open jobs ranked for the signed-in seeker. SQL first narrows to jobs the seeker has not applied to that
     * are remote or in a preferred country (newest first, at most {@code matching.max-candidates}); only those are sent
     * to the engine, best first. A seeker with no skills and no preferences still gets an answer.
     */
    public PagedResponse<RecommendedJobResponse> recommend(String username, Integer page, Integer size) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 20 : size;
        if (pageNumber < 0) {
            throw new IllegalArgumentException("page must be 0 or more.");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        Seeker seeker = requireSeeker(username, "see recommended jobs");

        List<Integer> ids = facts.narrow(username, facts.preferredCountries(seeker), properties.maxCandidates());
        if (ids.isEmpty()) {
            return PagedResponse.of(List.of(), pageNumber, pageSize, 0);
        }
        List<JobWithFacts> candidates = facts.jobs(ids);
        SeekerFacts seekerFacts = facts.seeker(seeker);

        // the remote call: no transaction is open here
        List<RankedJob> ranked = engine.rankJobs(seekerFacts, candidates.stream().map(JobWithFacts::facts).toList());

        Map<Integer, JobWithFacts> byId = new HashMap<>();
        candidates.forEach(c -> byId.put(c.facts().jobId(), c));
        List<RecommendedJobResponse> all = ranked.stream()
                .filter(r -> byId.containsKey(r.jobId()))
                .map(r -> toRecommended(byId.get(r.jobId()), r.result()))
                .toList();

        int from = (int) Math.min((long) pageNumber * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        return PagedResponse.of(all.subList(from, to), pageNumber, pageSize, all.size());
    }

    /**
     * The applicants of a job, best fit first, for the company that posted it. Only seekers who applied (and have not
     * withdrawn) are ever ranked; there is no search across all seekers. The newest {@code matching.max-applicants}
     * applications are ranked and then paged.
     */
    public PagedResponse<CandidateResponse> candidatesFor(String username, int jobId, Integer page, Integer size) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 20 : size;
        if (pageNumber < 0) {
            throw new IllegalArgumentException("page must be 0 or more.");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        JobFacts job = jobAccess.managedJob(username, jobId);
        List<ApplicantWithFacts> applicants = facts.applicants(jobId, properties.maxApplicants());
        if (applicants.isEmpty()) {
            return PagedResponse.of(List.of(), pageNumber, pageSize, 0);
        }

        // the remote call: no transaction is open here
        List<RankedCandidate> ranked = engine.rankCandidates(job, applicants.stream().map(ApplicantWithFacts::facts).toList());

        Map<String, ApplicantWithFacts> bySeeker = new HashMap<>();
        applicants.forEach(a -> bySeeker.put(a.seekerId(), a));
        Map<String, SkillRef> skillsById = job.requiredSkills().stream()
                .collect(Collectors.toMap(SkillRef::id, Function.identity(), (x, y) -> x));
        List<CandidateResponse> all = ranked.stream()
                .filter(r -> bySeeker.containsKey(r.seekerId()))
                .map(r -> {
                    ApplicantWithFacts a = bySeeker.get(r.seekerId());
                    return new CandidateResponse(a.applicationId(), a.status(), a.appliedAt(),
                            new CandidateResponse.Applicant(a.seekerId(), a.username(), a.name(), a.lastName(), a.hasCv()),
                            r.result().score(), names(r.result().matchedSkillIds(), skillsById),
                            names(r.result().missingSkillIds(), skillsById));
                })
                .toList();

        int from = (int) Math.min((long) pageNumber * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        return PagedResponse.of(all.subList(from, to), pageNumber, pageSize, all.size());
    }

    private static RecommendedJobResponse toRecommended(JobWithFacts job, MatchResult result) {
        Map<String, SkillRef> byId = job.facts().requiredSkills().stream()
                .collect(Collectors.toMap(SkillRef::id, Function.identity(), (a, b) -> a));
        return new RecommendedJobResponse(job.response(), result.score(),
                names(result.matchedSkillIds(), byId), names(result.missingSkillIds(), byId));
    }

    private static List<String> names(List<String> ids, Map<String, SkillRef> byId) {
        return ids.stream().map(id -> byId.containsKey(id) ? byId.get(id).name() : id).toList();
    }

    private Seeker requireSeeker(String username, String action) {
        return seekerRepo.findByUsername(username).orElseThrow(() -> {
            log.warn("Denied: user '{}' tried to {} without the SEEKER role", username, action);
            auditLog.event(username, "ACCESS_DENIED", "action='" + action + "' reason=not a seeker");
            return new LicenseValidationException("Only job seekers can " + action + ".");
        });
    }

    private static JobMatchResponse toResponse(JobFacts job, MatchResult result) {
        Map<String, SkillRef> byId = job.requiredSkills().stream()
                .collect(Collectors.toMap(SkillRef::id, Function.identity(), (a, b) -> a));
        return new JobMatchResponse(
                job.jobId(), result.score(),
                skills(result.matchedSkillIds(), byId), skills(result.missingSkillIds(), byId),
                result.parts().stream()
                        .map(p -> new JobMatchResponse.Part(p.key(), p.weight(), p.value(), p.counted())).toList());
    }

    private static List<JobMatchResponse.SkillRef> skills(List<String> ids, Map<String, SkillRef> byId) {
        return ids.stream()
                .map(id -> new JobMatchResponse.SkillRef(id, byId.containsKey(id) ? byId.get(id).name() : id))
                .toList();
    }
}
