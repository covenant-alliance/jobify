package com.mcverse.jobify.match;

import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.common.exception.LicenseValidationException;
import com.mcverse.jobify.common.exception.ResourceNotFoundException;
import com.mcverse.jobify.job.model.JobPost;
import com.mcverse.jobify.job.repository.JobRepo;
import com.mcverse.jobify.match.MatchFacts.JobFacts;
import com.mcverse.jobify.match.MatchFacts.MatchResult;
import com.mcverse.jobify.match.MatchFacts.SeekerFacts;
import com.mcverse.jobify.match.MatchFacts.SkillRef;
import com.mcverse.jobify.match.dto.JobMatchResponse;
import com.mcverse.jobify.user.model.Seeker;
import com.mcverse.jobify.user.repository.SeekerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    public MatchService(SeekerRepository seekerRepo, JobRepo jobRepo, MatchFactsLoader facts, MatchEngine engine,
                        AuditLog auditLog) {
        this.seekerRepo = seekerRepo;
        this.jobRepo = jobRepo;
        this.facts = facts;
        this.engine = engine;
        this.auditLog = auditLog;
    }

    /** The signed-in seeker's match with one open job. */
    public JobMatchResponse matchFor(String username, int jobId) {
        Seeker seeker = seekerRepo.findByUsername(username).orElseThrow(() -> {
            log.warn("Denied: user '{}' asked for a match score without the SEEKER role", username);
            auditLog.event(username, "ACCESS_DENIED", "action='see match scores' reason=not a seeker");
            return new LicenseValidationException("Only job seekers can see match scores.");
        });
        JobPost job = jobRepo.findById(jobId).filter(JobPost::isAvailable)
                .orElseThrow(() -> new ResourceNotFoundException("JobPost", Integer.toString(jobId)));

        SeekerFacts seekerFacts = facts.seeker(seeker);
        JobFacts jobFacts = facts.job(job);

        MatchResult result = engine.score(seekerFacts, jobFacts);
        return toResponse(jobFacts, result);
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
