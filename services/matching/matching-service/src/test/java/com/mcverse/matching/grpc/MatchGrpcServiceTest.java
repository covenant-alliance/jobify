package com.mcverse.matching.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mcverse.matching.score.Ranker;
import com.mcverse.matching.score.Scorer;
import com.mcverse.matching.score.ScoringWeights;
import com.mcverse.matching.v1.JobProfile;
import com.mcverse.matching.v1.Proficiency;
import com.mcverse.matching.v1.RankCandidatesRequest;
import com.mcverse.matching.v1.RankCandidatesResponse;
import com.mcverse.matching.v1.RankJobsRequest;
import com.mcverse.matching.v1.RankJobsResponse;
import com.mcverse.matching.v1.RequiredSkill;
import com.mcverse.matching.v1.ScoreRequest;
import com.mcverse.matching.v1.ScoreResponse;
import com.mcverse.matching.v1.SeekerProfile;
import com.mcverse.matching.v1.SeekerSkill;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Calls the adapter directly: status codes, limits and ordering, with no network. */
class MatchGrpcServiceTest {

    private final MatchGrpcService service = new MatchGrpcService(
            new Scorer(ScoringWeights.defaults()), new Ranker(new Scorer(ScoringWeights.defaults())));

    /** Collects what the service sends back. */
    private static final class Capture<T> implements StreamObserver<T> {
        T value;
        Throwable error;
        boolean completed;

        @Override public void onNext(T v) { value = v; }
        @Override public void onError(Throwable t) { error = t; }
        @Override public void onCompleted() { completed = true; }

        Status.Code code() {
            return ((StatusRuntimeException) error).getStatus().getCode();
        }
    }

    private static SeekerProfile seeker(String id, String... skills) {
        SeekerProfile.Builder b = SeekerProfile.newBuilder().setSeekerId(id);
        for (String s : skills) {
            b.addSkills(SeekerSkill.newBuilder().setSkillId(s).setProficiency(Proficiency.PROFICIENCY_ADVANCED));
        }
        return b.build();
    }

    private static JobProfile job(int id, String... skills) {
        JobProfile.Builder b = JobProfile.newBuilder().setJobId(id);
        for (String s : skills) {
            b.addRequiredSkills(RequiredSkill.newBuilder().setSkillId(s));
        }
        return b.build();
    }

    private static List<JobProfile> jobs(int n) {
        List<JobProfile> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            list.add(job(i, "a"));
        }
        return list;
    }

    // ---- Score ----

    @Test
    void scoreReturnsTheScorersAnswer() {
        Capture<ScoreResponse> out = new Capture<>();
        service.score(ScoreRequest.newBuilder().setSeeker(seeker("s", "a")).setJob(job(1, "a")).build(), out);

        assertEquals(100, out.value.getMatch().getScore());
        assertEquals(List.of("a"), out.value.getMatch().getMatchedSkillIdsList());
        assertEquals(true, out.completed);
        assertNull(out.error);
    }

    @Test
    void scoreWithUnknownSkillIdsAnswersZeroNotAnError() {
        Capture<ScoreResponse> out = new Capture<>();
        service.score(ScoreRequest.newBuilder().setSeeker(seeker("s", "zzz")).setJob(job(1, "a")).build(), out);
        assertEquals(0, out.value.getMatch().getScore());
        assertNull(out.error);
    }

    @Test
    void malformedScoreRequestsAreInvalidArgument() {
        List<ScoreRequest> bad = List.of(
                ScoreRequest.getDefaultInstance(),
                ScoreRequest.newBuilder().setSeeker(seeker("s")).build(),
                ScoreRequest.newBuilder().setJob(job(1)).build(),
                ScoreRequest.newBuilder().setSeeker(seeker(" ")).setJob(job(1)).build(),
                ScoreRequest.newBuilder().setSeeker(seeker("s")).setJob(job(0)).build());
        for (ScoreRequest request : bad) {
            Capture<ScoreResponse> out = new Capture<>();
            service.score(request, out);
            assertEquals(Status.Code.INVALID_ARGUMENT, out.code(), request.toString());
        }
    }

    // ---- RankJobs ----

    @Test
    void rankJobsOrdersAndLimits() {
        Capture<RankJobsResponse> out = new Capture<>();
        service.rankJobs(RankJobsRequest.newBuilder().setSeeker(seeker("s", "a"))
                .addJobs(job(1, "b")).addJobs(job(2, "a")).addJobs(job(3, "a", "b")).setLimit(2).build(), out);

        assertEquals(List.of(2, 3), out.value.getJobsList().stream().map(j -> j.getJobId()).toList());
    }

    @Test
    void rankJobsAcceptsExactlyTheLimitAndRefusesOneMore() {
        Capture<RankJobsResponse> ok = new Capture<>();
        service.rankJobs(RankJobsRequest.newBuilder().setSeeker(seeker("s", "a"))
                .addAllJobs(jobs(MatchGrpcService.MAX_ITEMS)).build(), ok);
        assertEquals(MatchGrpcService.MAX_ITEMS, ok.value.getJobsCount());

        Capture<RankJobsResponse> tooMany = new Capture<>();
        service.rankJobs(RankJobsRequest.newBuilder().setSeeker(seeker("s", "a"))
                .addAllJobs(jobs(MatchGrpcService.MAX_ITEMS + 1)).build(), tooMany);
        assertEquals(Status.Code.RESOURCE_EXHAUSTED, tooMany.code());
    }

    @Test
    void emptyJobListIsAnEmptyAnswer() {
        Capture<RankJobsResponse> out = new Capture<>();
        service.rankJobs(RankJobsRequest.newBuilder().setSeeker(seeker("s")).build(), out);
        assertEquals(0, out.value.getJobsCount());
    }

    @Test
    void malformedRankJobsRequestsAreInvalidArgument() {
        List<RankJobsRequest> bad = List.of(
                RankJobsRequest.getDefaultInstance(),
                RankJobsRequest.newBuilder().setSeeker(seeker("")).build(),
                RankJobsRequest.newBuilder().setSeeker(seeker("s")).setLimit(-1).build(),
                RankJobsRequest.newBuilder().setSeeker(seeker("s")).addJobs(job(0)).build());
        for (RankJobsRequest request : bad) {
            Capture<RankJobsResponse> out = new Capture<>();
            service.rankJobs(request, out);
            assertEquals(Status.Code.INVALID_ARGUMENT, out.code(), request.toString());
        }
    }

    // ---- RankCandidates ----

    @Test
    void rankCandidatesOrdersAndLimits() {
        Capture<RankCandidatesResponse> out = new Capture<>();
        service.rankCandidates(RankCandidatesRequest.newBuilder().setJob(job(1, "a"))
                .addCandidates(seeker("z", "a")).addCandidates(seeker("m")).addCandidates(seeker("b", "a"))
                .setLimit(2).build(), out);

        assertEquals(List.of("b", "z"), out.value.getCandidatesList().stream().map(c -> c.getSeekerId()).toList());
    }

    @Test
    void rankCandidatesRefusesMoreThanTheLimit() {
        RankCandidatesRequest.Builder request = RankCandidatesRequest.newBuilder().setJob(job(1, "a"));
        for (int i = 0; i <= MatchGrpcService.MAX_ITEMS; i++) {
            request.addCandidates(seeker("s" + i));
        }
        Capture<RankCandidatesResponse> out = new Capture<>();
        service.rankCandidates(request.build(), out);
        assertEquals(Status.Code.RESOURCE_EXHAUSTED, out.code());
    }

    @Test
    void malformedRankCandidatesRequestsAreInvalidArgument() {
        List<RankCandidatesRequest> bad = List.of(
                RankCandidatesRequest.getDefaultInstance(),
                RankCandidatesRequest.newBuilder().setJob(job(0)).build(),
                RankCandidatesRequest.newBuilder().setJob(job(1)).setLimit(-5).build(),
                RankCandidatesRequest.newBuilder().setJob(job(1)).addCandidates(seeker("")).build());
        for (RankCandidatesRequest request : bad) {
            Capture<RankCandidatesResponse> out = new Capture<>();
            service.rankCandidates(request, out);
            assertEquals(Status.Code.INVALID_ARGUMENT, out.code(), request.toString());
        }
    }

    // ---- unexpected failures ----

    @Test
    void anUnexpectedExceptionBecomesInternalWithoutLeakingDetails() {
        // A ranker with no scorer fails with a NullPointerException, standing in for any bug.
        MatchGrpcService failing = new MatchGrpcService(new Scorer(ScoringWeights.defaults()), new Ranker(null));
        Capture<RankJobsResponse> out = new Capture<>();
        failing.rankJobs(RankJobsRequest.newBuilder().setSeeker(seeker("s")).addJobs(job(1)).build(), out);

        assertEquals(Status.Code.INTERNAL, out.code());
        assertEquals("Internal error", ((StatusRuntimeException) out.error).getStatus().getDescription());
    }
}
