package com.mcverse.jobify.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcverse.jobify.common.exception.ServiceUnavailableException;
import com.mcverse.jobify.common.model.EmploymentType;
import com.mcverse.jobify.job.model.ExperienceLevel;
import com.mcverse.jobify.job.model.WorkMode;
import com.mcverse.jobify.match.MatchFacts.JobFacts;
import com.mcverse.jobify.match.MatchFacts.MatchResult;
import com.mcverse.jobify.match.MatchFacts.PreferenceFacts;
import com.mcverse.jobify.match.MatchFacts.SeekerFacts;
import com.mcverse.jobify.match.MatchFacts.SeekerSkillFact;
import com.mcverse.jobify.match.MatchFacts.SkillRef;
import com.mcverse.jobify.user.model.ProficiencyLevel;
import com.mcverse.matching.v1.Match;
import com.mcverse.matching.v1.MatchServiceGrpc;
import com.mcverse.matching.v1.Proficiency;
import com.mcverse.matching.v1.RankJobsRequest;
import com.mcverse.matching.v1.RankJobsResponse;
import com.mcverse.matching.v1.RankedJob;
import com.mcverse.matching.v1.ScorePart;
import com.mcverse.matching.v1.ScoreRequest;
import com.mcverse.matching.v1.ScoreResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.grpc.Metadata;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * GrpcMatchEngine against a fake matching service in the same process. The fake can answer, be slow, or fail on
 * demand, which is how the deadline, retry and circuit-breaker rules are proven without a network.
 */
class GrpcMatchEngineTest {

    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> REQUEST_ID = Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);

    /** What the fake does with the next calls. */
    private interface Behaviour {
        void handle(ScoreRequest request, StreamObserver<ScoreResponse> out) throws Exception;
    }

    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<ScoreRequest> received = new AtomicReference<>();
    private final AtomicReference<Metadata> headers = new AtomicReference<>();
    private volatile Behaviour behaviour = (request, out) -> ok(out, 76);
    private final AtomicInteger rankCalls = new AtomicInteger();
    private final AtomicReference<RankJobsRequest> rankReceived = new AtomicReference<>();
    private volatile Consumer<StreamObserver<RankJobsResponse>> rankBehaviour = out -> {
        out.onNext(RankJobsResponse.newBuilder()
                .addJobs(RankedJob.newBuilder().setJobId(2).setMatch(Match.newBuilder().setScore(90)))
                .addJobs(RankedJob.newBuilder().setJobId(1).setMatch(Match.newBuilder().setScore(40)
                        .addMissingSkillIds("kafka"))).build());
        out.onCompleted();
    };

    private Server server;
    private ManagedChannel channel;

    private static void ok(StreamObserver<ScoreResponse> out, int score) {
        out.onNext(ScoreResponse.newBuilder().setMatch(Match.newBuilder().setScore(score)
                .addMatchedSkillIds("java").addMissingSkillIds("kafka")
                .addParts(ScorePart.newBuilder().setKey("skills").setWeight(60).setValue(0.5).setCounted(true))
                .addParts(ScorePart.newBuilder().setKey("rate").setWeight(10).setCounted(false))).build());
        out.onCompleted();
    }

    @BeforeEach
    void start() throws Exception {
        String name = "matching-" + UUID.randomUUID();
        MatchServiceGrpc.MatchServiceImplBase fake = new MatchServiceGrpc.MatchServiceImplBase() {
            @Override
            public void rankJobs(RankJobsRequest request, StreamObserver<RankJobsResponse> out) {
                rankCalls.incrementAndGet();
                rankReceived.set(request);
                try {
                    rankBehaviour.accept(out);
                } catch (RuntimeException e) {
                    // the client gave up or the fake was told to fail; nothing more to answer
                }
            }

            @Override
            public void score(ScoreRequest request, StreamObserver<ScoreResponse> out) {
                calls.incrementAndGet();
                received.set(request);
                try {
                    behaviour.handle(request, out);
                } catch (Exception e) {
                    try {
                        out.onError(Status.INTERNAL.withDescription(String.valueOf(e.getMessage())).asRuntimeException());
                    } catch (RuntimeException alreadyCancelled) {
                        // the client gave up (deadline); nothing to answer
                    }
                }
            }
        };
        ServerInterceptor capture = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
                headers.set(metadata);
                return next.startCall(call, metadata);
            }
        };
        server = InProcessServerBuilder.forName(name)
                .addService(ServerInterceptors.intercept(fake, capture)).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterEach
    void stop() throws Exception {
        MDC.clear();
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    private GrpcMatchEngine engine(Duration deadline, int window, int minimum, Duration openFor) {
        return new GrpcMatchEngine(channel, new MatchProperties(true, "unused", "secret-token", deadline,
                Duration.ofSeconds(2), 200, new MatchProperties.Breaker(window, minimum, 50, openFor)));
    }

    private GrpcMatchEngine engine() {
        return engine(Duration.ofSeconds(2), 10, 5, Duration.ofSeconds(30));
    }

    private static SeekerFacts seeker() {
        return new SeekerFacts("seeker-1",
                List.of(new SeekerSkillFact("java", ProficiencyLevel.EXPERT, 4.5), new SeekerSkillFact("sql", ProficiencyLevel.BEGINNER, 0)),
                new PreferenceFacts(List.of("DE", "NL"), List.of(WorkMode.REMOTE, WorkMode.HYBRID),
                        List.of(EmploymentType.B2B), 60000.0, null, ExperienceLevel.LEAD));
    }

    private static JobFacts job() {
        return new JobFacts(42, "Backend Engineer", List.of(new SkillRef("java", "Java"), new SkillRef("kafka", "Kafka")),
                "DE", WorkMode.ONSITE, EmploymentType.TEMPORARY, 70000.0, ExperienceLevel.INTERN, 1760000000L);
    }

    // ---- translation ----

    @Test
    void facts_are_translated_to_the_contract_and_the_answer_back() {
        MatchResult result = engine().score(seeker(), job());

        ScoreRequest sent = received.get();
        assertEquals("seeker-1", sent.getSeeker().getSeekerId());
        assertEquals(Proficiency.PROFICIENCY_EXPERT, sent.getSeeker().getSkills(0).getProficiency());
        assertEquals(4.5, sent.getSeeker().getSkills(0).getYears());
        assertEquals(Proficiency.PROFICIENCY_BEGINNER, sent.getSeeker().getSkills(1).getProficiency());
        var prefs = sent.getSeeker().getPreferences();
        assertEquals(List.of("DE", "NL"), prefs.getCountryCodesList());
        assertEquals(2, prefs.getWorkModesCount());
        assertEquals(com.mcverse.matching.v1.EmploymentType.EMPLOYMENT_TYPE_B2B, prefs.getEmploymentTypes(0));
        assertEquals(60000.0, prefs.getMinAnnualRate());
        assertTrue(!prefs.hasMaxAnnualRate(), "an absent bound stays absent");
        assertEquals(com.mcverse.matching.v1.ExperienceLevel.EXPERIENCE_LEVEL_LEAD, prefs.getExperienceLevel());

        var sentJob = sent.getJob();
        assertEquals(42, sentJob.getJobId());
        assertEquals("Kafka", sentJob.getRequiredSkills(1).getName());
        assertEquals("DE", sentJob.getCountryCode());
        assertEquals(com.mcverse.matching.v1.WorkMode.WORK_MODE_ONSITE, sentJob.getWorkMode());
        assertEquals(com.mcverse.matching.v1.EmploymentType.EMPLOYMENT_TYPE_TEMPORARY, sentJob.getEmploymentType());
        assertEquals(70000.0, sentJob.getAnnualRate());
        assertEquals(1760000000L, sentJob.getPostedAtEpochSeconds());

        assertEquals(76, result.score());
        assertEquals(List.of("java"), result.matchedSkillIds());
        assertEquals(List.of("kafka"), result.missingSkillIds());
        assertEquals(2, result.parts().size());
        assertEquals("skills", result.parts().get(0).key());
        assertTrue(result.parts().get(0).counted());
        assertTrue(!result.parts().get(1).counted());
    }

    @Test
    void absent_preferences_and_job_fields_are_left_out_of_the_request() {
        SeekerFacts noPrefs = new SeekerFacts("s", List.of(), null);
        JobFacts bare = new JobFacts(7, "t", List.of(), null, null, null, null, null, 0);

        engine().score(noPrefs, bare);

        ScoreRequest sent = received.get();
        assertTrue(!sent.getSeeker().hasPreferences());
        assertEquals("", sent.getJob().getCountryCode());
        assertEquals(com.mcverse.matching.v1.WorkMode.WORK_MODE_UNSPECIFIED, sent.getJob().getWorkMode());
        assertTrue(!sent.getJob().hasAnnualRate());
        assertTrue(!sent.getJob().hasExperienceLevel());
    }

    // ---- metadata ----

    @Test
    void the_token_and_the_request_id_travel_with_the_call() {
        MDC.put("requestId", "req-123");
        engine().score(seeker(), job());

        assertEquals("Bearer secret-token", headers.get().get(AUTHORIZATION));
        assertEquals("req-123", headers.get().get(REQUEST_ID));
    }

    @Test
    void no_request_id_header_without_one_in_the_log_context() {
        engine().score(seeker(), job());
        assertNull(headers.get().get(REQUEST_ID));
    }

    // ---- failure rules ----

    @Test
    void a_slow_service_is_cut_off_at_the_deadline_without_a_retry() {
        behaviour = (request, out) -> Thread.sleep(1500);
        GrpcMatchEngine engine = engine(Duration.ofMillis(100), 10, 5, Duration.ofSeconds(30));

        ServiceUnavailableException e = assertThrows(ServiceUnavailableException.class, () -> engine.score(seeker(), job()));

        assertEquals(GrpcMatchEngine.UNAVAILABLE_MESSAGE, e.getMessage());
        assertEquals(1, calls.get(), "a timeout is not retried: the first attempt may still be running");
    }

    @Test
    void one_refused_connection_is_retried_once_and_can_succeed() {
        AtomicInteger seen = new AtomicInteger();
        behaviour = (request, out) -> {
            if (seen.incrementAndGet() == 1) {
                out.onError(Status.UNAVAILABLE.asRuntimeException());
            } else {
                ok(out, 55);
            }
        };

        assertEquals(55, engine().score(seeker(), job()).score());
        assertEquals(2, calls.get());
    }

    @Test
    void two_refusals_in_a_row_end_in_503_after_exactly_two_attempts() {
        behaviour = (request, out) -> out.onError(Status.UNAVAILABLE.asRuntimeException());

        assertThrows(ServiceUnavailableException.class, () -> engine().score(seeker(), job()));
        assertEquals(2, calls.get());
    }

    @Test
    void other_failures_are_not_retried() {
        for (Status status : new Status[] {Status.INTERNAL, Status.UNAUTHENTICATED, Status.INVALID_ARGUMENT,
                Status.RESOURCE_EXHAUSTED}) {
            calls.set(0);
            behaviour = (request, out) -> out.onError(status.asRuntimeException());
            ServiceUnavailableException e = assertThrows(ServiceUnavailableException.class,
                    () -> engine().score(seeker(), job()), status.getCode().name());
            assertEquals(GrpcMatchEngine.UNAVAILABLE_MESSAGE, e.getMessage());
            assertEquals(1, calls.get(), status.getCode().name());
        }
    }

    @Test
    void the_breaker_opens_after_repeated_failures_and_stops_calling() {
        behaviour = (request, out) -> out.onError(Status.INTERNAL.asRuntimeException());
        GrpcMatchEngine engine = engine(Duration.ofSeconds(2), 4, 4, Duration.ofSeconds(30));

        for (int i = 0; i < 4; i++) {
            assertThrows(ServiceUnavailableException.class, () -> engine.score(seeker(), job()));
        }
        assertEquals(CircuitBreaker.State.OPEN, engine.breakerState());
        int before = calls.get();

        ServiceUnavailableException e = assertThrows(ServiceUnavailableException.class, () -> engine.score(seeker(), job()));
        assertEquals(GrpcMatchEngine.UNAVAILABLE_MESSAGE, e.getMessage());
        assertEquals(before, calls.get(), "an open breaker must not reach the service");
    }

    @Test
    void the_breaker_tries_again_after_the_wait_and_closes_when_the_service_is_back() throws Exception {
        AtomicInteger seen = new AtomicInteger();
        behaviour = (request, out) -> {
            if (seen.incrementAndGet() <= 2) {
                out.onError(Status.INTERNAL.asRuntimeException());
            } else {
                ok(out, 90);
            }
        };
        GrpcMatchEngine engine = engine(Duration.ofSeconds(2), 2, 2, Duration.ofMillis(150));

        assertThrows(ServiceUnavailableException.class, () -> engine.score(seeker(), job()));
        assertThrows(ServiceUnavailableException.class, () -> engine.score(seeker(), job()));
        assertEquals(CircuitBreaker.State.OPEN, engine.breakerState());

        Thread.sleep(250);

        assertEquals(90, engine.score(seeker(), job()).score());
        assertEquals(90, engine.score(seeker(), job()).score());
        assertEquals(CircuitBreaker.State.CLOSED, engine.breakerState());
    }

    @Test
    void a_service_that_is_not_there_at_all_is_a_503() throws Exception {
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);

        assertThrows(ServiceUnavailableException.class, () -> engine().score(seeker(), job()));
    }

    @Test
    void breaker_state_starts_closed() {
        assertEquals(CircuitBreaker.State.CLOSED, engine().breakerState());
    }

    // ---- ranking ----

    private static JobFacts jobWithId(int id) {
        return new JobFacts(id, "job " + id, List.of(new SkillRef("kafka", "Kafka")), "DE", WorkMode.REMOTE,
                EmploymentType.FULL_TIME, null, null, 0);
    }

    @Test
    void ranking_sends_all_jobs_in_one_call_and_returns_the_service_order() {
        List<MatchFacts.RankedJob> ranked = engine().rankJobs(seeker(), List.of(jobWithId(1), jobWithId(2)));

        RankJobsRequest sent = rankReceived.get();
        assertEquals(2, sent.getJobsCount());
        assertEquals(1, sent.getJobs(0).getJobId());
        assertEquals("seeker-1", sent.getSeeker().getSeekerId());
        assertEquals(0, sent.getLimit(), "Jobify pages itself, so it asks for everything");
        assertEquals(List.of(2, 1), ranked.stream().map(MatchFacts.RankedJob::jobId).toList());
        assertEquals(90, ranked.get(0).result().score());
        assertEquals(List.of("kafka"), ranked.get(1).result().missingSkillIds());
        assertEquals(1, rankCalls.get());
    }

    @Test
    void ranking_nothing_does_not_call_the_service() {
        assertEquals(List.of(), engine().rankJobs(seeker(), List.of()));
        assertEquals(0, rankCalls.get());
    }

    @Test
    void ranking_carries_the_token_too() {
        engine().rankJobs(seeker(), List.of(jobWithId(1)));
        assertEquals("Bearer secret-token", headers.get().get(AUTHORIZATION));
    }

    @Test
    void ranking_uses_its_own_longer_deadline() {
        // a call slower than the single-job deadline (100 ms) but faster than the ranking deadline (2 s) succeeds
        rankBehaviour = out -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            out.onNext(RankJobsResponse.getDefaultInstance());
            out.onCompleted();
        };
        GrpcMatchEngine engine = engine(Duration.ofMillis(100), 10, 5, Duration.ofSeconds(30));

        assertEquals(List.of(), engine.rankJobs(seeker(), List.of(jobWithId(1))));
    }

    @Test
    void ranking_that_is_too_slow_is_a_503() {
        rankBehaviour = out -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        GrpcMatchEngine engine = new GrpcMatchEngine(channel, new MatchProperties(true, "u", "t",
                Duration.ofSeconds(2), Duration.ofMillis(100), 200, new MatchProperties.Breaker(10, 5, 50, Duration.ofSeconds(30))));

        assertThrows(ServiceUnavailableException.class, () -> engine.rankJobs(seeker(), List.of(jobWithId(1))));
    }

    @Test
    void ranking_failures_and_single_job_failures_share_one_breaker() {
        behaviour = (request, out) -> out.onError(Status.INTERNAL.asRuntimeException());
        rankBehaviour = out -> out.onError(Status.INTERNAL.asRuntimeException());
        GrpcMatchEngine engine = engine(Duration.ofSeconds(2), 4, 4, Duration.ofSeconds(30));

        assertThrows(ServiceUnavailableException.class, () -> engine.score(seeker(), job()));
        assertThrows(ServiceUnavailableException.class, () -> engine.rankJobs(seeker(), List.of(jobWithId(1))));
        assertThrows(ServiceUnavailableException.class, () -> engine.score(seeker(), job()));
        assertThrows(ServiceUnavailableException.class, () -> engine.rankJobs(seeker(), List.of(jobWithId(1))));

        assertEquals(CircuitBreaker.State.OPEN, engine.breakerState());
    }

    @Test
    void ranking_retries_once_on_a_refused_connection() {
        AtomicInteger seen = new AtomicInteger();
        rankBehaviour = out -> {
            if (seen.incrementAndGet() == 1) {
                out.onError(Status.UNAVAILABLE.asRuntimeException());
            } else {
                out.onNext(RankJobsResponse.getDefaultInstance());
                out.onCompleted();
            }
        };

        assertEquals(List.of(), engine().rankJobs(seeker(), List.of(jobWithId(1))));
        assertEquals(2, rankCalls.get());
    }
}
