package com.mcverse.matching.grpc;

import com.mcverse.matching.score.Ranker;
import com.mcverse.matching.score.Scorer;
import com.mcverse.matching.v1.MatchServiceGrpc;
import com.mcverse.matching.v1.RankCandidatesRequest;
import com.mcverse.matching.v1.RankCandidatesResponse;
import com.mcverse.matching.v1.RankJobsRequest;
import com.mcverse.matching.v1.RankJobsResponse;
import com.mcverse.matching.v1.ScoreRequest;
import com.mcverse.matching.v1.ScoreResponse;
import com.mcverse.matching.v1.Match;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The gRPC adapter: checks the request, calls the pure scoring core, maps problems to status codes.
 * No business rule lives here. Logs hold counts and ids only, never names, emails or CV text.
 */
@Service
public class MatchGrpcService extends MatchServiceGrpc.MatchServiceImplBase {

    /** Most jobs or candidates one request may carry (see docs/MATCHING_SERVICE.md, section 4). */
    public static final int MAX_ITEMS = 500;

    private static final Logger log = LoggerFactory.getLogger(MatchGrpcService.class);

    private final Scorer scorer;
    private final Ranker ranker;
    private final MeterRegistry meters;
    private final DistributionSummary scores;

    public MatchGrpcService(Scorer scorer, Ranker ranker, MeterRegistry meters) {
        this.scorer = scorer;
        this.ranker = ranker;
        this.meters = meters;
        // how the scores are spread: ten buckets of ten points, so a shift in the weights shows up on a dashboard
        this.scores = DistributionSummary.builder("matching.score")
                .description("Scores handed out, 0 to 100")
                .serviceLevelObjectives(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
                .register(meters);
    }

    @Override
    public void score(ScoreRequest request, StreamObserver<ScoreResponse> out) {
        respond("Score", "Score", out, () -> {
            require(request.hasSeeker() && request.hasJob(), "seeker and job are required");
            requireSeekerId(request.getSeeker().getSeekerId());
            requireJobId(request.getJob().getJobId());
            Match match = scorer.score(request.getSeeker(), request.getJob());
            scores.record(match.getScore());
            return ScoreResponse.newBuilder().setMatch(match).build();
        });
    }

    @Override
    public void rankJobs(RankJobsRequest request, StreamObserver<RankJobsResponse> out) {
        respond("RankJobs jobs=" + request.getJobsCount(), "RankJobs", out, () -> {
            tooMany(request.getJobsCount(), "jobs");
            require(request.hasSeeker(), "seeker is required");
            requireSeekerId(request.getSeeker().getSeekerId());
            require(request.getLimit() >= 0, "limit must not be negative");
            request.getJobsList().forEach(job -> requireJobId(job.getJobId()));
            var ranked = ranker.rankJobs(request.getSeeker(), request.getJobsList(), request.getLimit());
            ranked.forEach(r -> scores.record(r.getMatch().getScore()));
            return RankJobsResponse.newBuilder().addAllJobs(ranked).build();
        });
    }

    @Override
    public void rankCandidates(RankCandidatesRequest request, StreamObserver<RankCandidatesResponse> out) {
        respond("RankCandidates candidates=" + request.getCandidatesCount(), "RankCandidates", out, () -> {
            tooMany(request.getCandidatesCount(), "candidates");
            require(request.hasJob(), "job is required");
            requireJobId(request.getJob().getJobId());
            require(request.getLimit() >= 0, "limit must not be negative");
            request.getCandidatesList().forEach(c -> requireSeekerId(c.getSeekerId()));
            var ranked = ranker.rankCandidates(request.getJob(), request.getCandidatesList(), request.getLimit());
            ranked.forEach(r -> scores.record(r.getMatch().getScore()));
            return RankCandidatesResponse.newBuilder().addAllCandidates(ranked).build();
        });
    }

    /** Runs one call, answers it, logs it and counts it: one place, so every rpc is measured the same way. */
    private <T> void respond(String what, String rpc, StreamObserver<T> out, Supplier<T> work) {
        Timer.Sample sample = Timer.start(meters);
        Status.Code outcome = Status.Code.OK;
        try {
            T result = work.get();
            log.info("{} ok", what);
            out.onNext(result);
            out.onCompleted();
        } catch (StatusRuntimeException e) {
            outcome = e.getStatus().getCode();
            log.info("{} rejected: {}", what, outcome);
            out.onError(e);
        } catch (RuntimeException e) {
            outcome = Status.Code.INTERNAL;
            // The cause is logged here; the caller only learns that it was our fault.
            log.error("{} failed", what, e);
            out.onError(Status.INTERNAL.withDescription("Internal error").asRuntimeException());
        } finally {
            meters.counter("matching.requests", "rpc", rpc, "outcome", outcome.name()).increment();
            sample.stop(Timer.builder("matching.request.duration").description("Time to answer one call")
                    .tags("rpc", rpc).publishPercentileHistogram().register(meters));
        }
    }

    private static void tooMany(int count, String what) {
        if (count > MAX_ITEMS) {
            throw Status.RESOURCE_EXHAUSTED
                    .withDescription("At most " + MAX_ITEMS + " " + what + " per request, got " + count)
                    .asRuntimeException();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw Status.INVALID_ARGUMENT.withDescription(message).asRuntimeException();
        }
    }

    private static void requireSeekerId(String id) {
        require(!id.isBlank(), "seeker_id is required");
    }

    private static void requireJobId(int id) {
        require(id > 0, "job_id must be positive");
    }
}
