package com.mcverse.jobify.match;

import com.mcverse.jobify.common.exception.ServiceUnavailableException;
import com.mcverse.jobify.common.logging.CorrelationFilter;
import com.mcverse.jobify.match.MatchFacts.JobFacts;
import com.mcverse.jobify.match.MatchFacts.MatchResult;
import com.mcverse.jobify.match.MatchFacts.PartFact;
import com.mcverse.jobify.match.MatchFacts.PreferenceFacts;
import com.mcverse.jobify.match.MatchFacts.RankedJob;
import com.mcverse.jobify.match.MatchFacts.SeekerFacts;
import com.mcverse.matching.v1.EmploymentType;
import com.mcverse.matching.v1.ExperienceLevel;
import com.mcverse.matching.v1.JobProfile;
import com.mcverse.matching.v1.Match;
import com.mcverse.matching.v1.MatchServiceGrpc;
import com.mcverse.matching.v1.Preferences;
import com.mcverse.matching.v1.RankJobsRequest;
import com.mcverse.matching.v1.Proficiency;
import com.mcverse.matching.v1.RequiredSkill;
import com.mcverse.matching.v1.ScorePart;
import com.mcverse.matching.v1.ScoreRequest;
import com.mcverse.matching.v1.SeekerProfile;
import com.mcverse.matching.v1.SeekerSkill;
import com.mcverse.matching.v1.WorkMode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The adapter between Jobify and the matching service. It translates Jobify's facts to the contract's messages (and
 * back) so neither side's internals leak into the other, and it owns the failure rules (docs/MATCHING_ARCHITECTURE.md,
 * section 5): a deadline per attempt, one retry only on UNAVAILABLE, and a circuit breaker over whole calls. Anything
 * that goes wrong is a {@link ServiceUnavailableException}.
 */
public class GrpcMatchEngine implements MatchEngine {

    public static final String UNAVAILABLE_MESSAGE =
            "Job matching is temporarily unavailable. Please try again in a moment.";

    private static final Logger log = LoggerFactory.getLogger(GrpcMatchEngine.class);
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> REQUEST_ID =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);

    private final MatchServiceGrpc.MatchServiceBlockingStub stub;
    private final MatchProperties properties;
    private final CircuitBreaker breaker;

    public GrpcMatchEngine(Channel channel, MatchProperties properties) {
        this.properties = properties;
        this.stub = MatchServiceGrpc.newBlockingStub(channel)
                .withInterceptors(new CallMetadata(properties.token()));
        MatchProperties.Breaker b = properties.breaker();
        this.breaker = CircuitBreaker.of("matching", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(b.windowSize())
                .minimumNumberOfCalls(b.minimumCalls())
                .failureRateThreshold(b.failureRatePercent())
                .waitDurationInOpenState(b.openFor())
                .permittedNumberOfCallsInHalfOpenState(2)
                .build());
    }

    /** For tests and metrics. */
    CircuitBreaker.State breakerState() {
        return breaker.getState();
    }

    @Override
    public MatchResult score(SeekerFacts seeker, JobFacts job) {
        ScoreRequest request = ScoreRequest.newBuilder().setSeeker(toProto(seeker)).setJob(toProto(job)).build();
        return guarded(() -> fromProto(withOneRetry(properties.deadline(),
                stub -> stub.score(request)).getMatch()));
    }

    @Override
    public List<RankedJob> rankJobs(SeekerFacts seeker, List<JobFacts> jobs) {
        if (jobs.isEmpty()) {
            return List.of();
        }
        RankJobsRequest.Builder request = RankJobsRequest.newBuilder().setSeeker(toProto(seeker));
        jobs.forEach(job -> request.addJobs(toProto(job)));
        RankJobsRequest built = request.build();
        return guarded(() -> withOneRetry(properties.rankDeadline(), stub -> stub.rankJobs(built)).getJobsList().stream()
                .map(r -> new RankedJob(r.getJobId(), fromProto(r.getMatch()))).toList());
    }

    /** Every failure, however it happens, is the same answer to the caller: unavailable. */
    private <T> T guarded(Supplier<T> call) {
        try {
            return breaker.executeSupplier(call);
        } catch (CallNotPermittedException e) {
            log.warn("Matching call refused: circuit breaker is open");
            throw new ServiceUnavailableException(UNAVAILABLE_MESSAGE, e);
        } catch (StatusRuntimeException e) {
            log.warn("Matching call failed: {}", e.getStatus().getCode());
            throw new ServiceUnavailableException(UNAVAILABLE_MESSAGE, e);
        }
    }

    /** The calls are read-only, so repeating one is harmless. Only a refused connection is retried, never a timeout. */
    private <T> T withOneRetry(Duration deadline, Function<MatchServiceGrpc.MatchServiceBlockingStub, T> call) {
        try {
            return call.apply(stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS));
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() != Status.Code.UNAVAILABLE) {
                throw e;
            }
            log.info("Matching service unavailable, retrying once");
            return call.apply(stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS));
        }
    }

    /** Adds the shared secret and the request id of the user's request, so one action can be followed in both logs. */
    private record CallMetadata(String token) implements ClientInterceptor {
        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions options, Channel next) {
            return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, options)) {
                @Override
                public void start(Listener<RespT> listener, Metadata headers) {
                    headers.put(AUTHORIZATION, "Bearer " + token);
                    String requestId = MDC.get(CorrelationFilter.REQUEST_ID);
                    if (requestId != null) {
                        headers.put(REQUEST_ID, requestId);
                    }
                    super.start(listener, headers);
                }
            };
        }
    }

    // ---- Jobify facts to the contract ----

    static SeekerProfile toProto(SeekerFacts seeker) {
        SeekerProfile.Builder b = SeekerProfile.newBuilder().setSeekerId(seeker.seekerId());
        seeker.skills().forEach(s -> b.addSkills(SeekerSkill.newBuilder()
                .setSkillId(s.skillId()).setProficiency(proficiency(s)).setYears(s.years())));
        PreferenceFacts p = seeker.preferences();
        if (p != null) {
            Preferences.Builder pb = Preferences.newBuilder().addAllCountryCodes(p.countryCodes());
            p.workModes().forEach(m -> pb.addWorkModes(WorkMode.valueOf("WORK_MODE_" + m.name())));
            p.employmentTypes().forEach(t -> pb.addEmploymentTypes(EmploymentType.valueOf("EMPLOYMENT_TYPE_" + t.name())));
            if (p.minAnnualRate() != null) pb.setMinAnnualRate(p.minAnnualRate());
            if (p.maxAnnualRate() != null) pb.setMaxAnnualRate(p.maxAnnualRate());
            if (p.experienceLevel() != null) {
                pb.setExperienceLevel(ExperienceLevel.valueOf("EXPERIENCE_LEVEL_" + p.experienceLevel().name()));
            }
            b.setPreferences(pb);
        }
        return b.build();
    }

    static JobProfile toProto(JobFacts job) {
        JobProfile.Builder b = JobProfile.newBuilder().setJobId(job.jobId()).setTitle(job.title())
                .setPostedAtEpochSeconds(job.postedAtEpochSeconds());
        job.requiredSkills().forEach(s -> b.addRequiredSkills(RequiredSkill.newBuilder().setSkillId(s.id()).setName(s.name())));
        if (job.countryCode() != null) b.setCountryCode(job.countryCode());
        if (job.workMode() != null) b.setWorkMode(WorkMode.valueOf("WORK_MODE_" + job.workMode().name()));
        if (job.employmentType() != null) {
            b.setEmploymentType(EmploymentType.valueOf("EMPLOYMENT_TYPE_" + job.employmentType().name()));
        }
        if (job.annualRate() != null) b.setAnnualRate(job.annualRate());
        if (job.experienceLevel() != null) {
            b.setExperienceLevel(ExperienceLevel.valueOf("EXPERIENCE_LEVEL_" + job.experienceLevel().name()));
        }
        return b.build();
    }

    private static Proficiency proficiency(MatchFacts.SeekerSkillFact skill) {
        return Proficiency.valueOf("PROFICIENCY_" + skill.level().name());
    }

    // ---- the contract to Jobify facts ----

    static MatchResult fromProto(Match match) {
        List<PartFact> parts = match.getPartsList().stream()
                .map((ScorePart p) -> new PartFact(p.getKey(), p.getWeight(), p.getValue(), p.getCounted())).toList();
        return new MatchResult(match.getScore(), List.copyOf(match.getMatchedSkillIdsList()),
                List.copyOf(match.getMissingSkillIdsList()), parts);
    }
}
