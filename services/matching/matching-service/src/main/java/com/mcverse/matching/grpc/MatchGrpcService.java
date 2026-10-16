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
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
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

    public MatchGrpcService(Scorer scorer, Ranker ranker) {
        this.scorer = scorer;
        this.ranker = ranker;
    }

    @Override
    public void score(ScoreRequest request, StreamObserver<ScoreResponse> out) {
        respond("Score", out, () -> {
            require(request.hasSeeker() && request.hasJob(), "seeker and job are required");
            requireSeekerId(request.getSeeker().getSeekerId());
            requireJobId(request.getJob().getJobId());
            return ScoreResponse.newBuilder().setMatch(scorer.score(request.getSeeker(), request.getJob())).build();
        });
    }

    @Override
    public void rankJobs(RankJobsRequest request, StreamObserver<RankJobsResponse> out) {
        respond("RankJobs jobs=" + request.getJobsCount(), out, () -> {
            tooMany(request.getJobsCount(), "jobs");
            require(request.hasSeeker(), "seeker is required");
            requireSeekerId(request.getSeeker().getSeekerId());
            require(request.getLimit() >= 0, "limit must not be negative");
            request.getJobsList().forEach(job -> requireJobId(job.getJobId()));
            return RankJobsResponse.newBuilder()
                    .addAllJobs(ranker.rankJobs(request.getSeeker(), request.getJobsList(), request.getLimit()))
                    .build();
        });
    }

    @Override
    public void rankCandidates(RankCandidatesRequest request, StreamObserver<RankCandidatesResponse> out) {
        respond("RankCandidates candidates=" + request.getCandidatesCount(), out, () -> {
            tooMany(request.getCandidatesCount(), "candidates");
            require(request.hasJob(), "job is required");
            requireJobId(request.getJob().getJobId());
            require(request.getLimit() >= 0, "limit must not be negative");
            request.getCandidatesList().forEach(c -> requireSeekerId(c.getSeekerId()));
            return RankCandidatesResponse.newBuilder()
                    .addAllCandidates(ranker.rankCandidates(request.getJob(), request.getCandidatesList(), request.getLimit()))
                    .build();
        });
    }

    private static <T> void respond(String what, StreamObserver<T> out, Supplier<T> work) {
        try {
            T result = work.get();
            log.info("{} ok", what);
            out.onNext(result);
            out.onCompleted();
        } catch (StatusRuntimeException e) {
            log.info("{} rejected: {}", what, e.getStatus().getCode());
            out.onError(e);
        } catch (RuntimeException e) {
            // The cause is logged here; the caller only learns that it was our fault.
            log.error("{} failed", what, e);
            out.onError(Status.INTERNAL.withDescription("Internal error").asRuntimeException());
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
