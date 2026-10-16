package com.mcverse.matching.score;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mcverse.matching.v1.JobProfile;
import com.mcverse.matching.v1.Proficiency;
import com.mcverse.matching.v1.RankedCandidate;
import com.mcverse.matching.v1.RankedJob;
import com.mcverse.matching.v1.RequiredSkill;
import com.mcverse.matching.v1.SeekerProfile;
import com.mcverse.matching.v1.SeekerSkill;
import java.util.List;
import org.junit.jupiter.api.Test;

class RankerTest {

    private final Ranker ranker = new Ranker(new Scorer(ScoringWeights.defaults()));

    private static JobProfile job(int id, long postedAt, String... skills) {
        JobProfile.Builder b = JobProfile.newBuilder().setJobId(id).setPostedAtEpochSeconds(postedAt);
        for (String s : skills) {
            b.addRequiredSkills(RequiredSkill.newBuilder().setSkillId(s));
        }
        return b.build();
    }

    private static SeekerProfile seeker(String id, String... skills) {
        SeekerProfile.Builder b = SeekerProfile.newBuilder().setSeekerId(id);
        for (String s : skills) {
            b.addSkills(SeekerSkill.newBuilder().setSkillId(s).setProficiency(Proficiency.PROFICIENCY_ADVANCED));
        }
        return b.build();
    }

    private static List<Integer> ids(List<RankedJob> ranked) {
        return ranked.stream().map(RankedJob::getJobId).toList();
    }

    @Test
    void bestScoreFirst() {
        List<RankedJob> ranked = ranker.rankJobs(seeker("s", "a"),
                List.of(job(1, 10, "b"), job(2, 10, "a"), job(3, 10, "a", "b")), 0);
        assertEquals(List.of(2, 3, 1), ids(ranked));
    }

    @Test
    void tiesGoToTheNewerJobThenTheLowerId() {
        List<RankedJob> ranked = ranker.rankJobs(seeker("s", "a"),
                List.of(job(5, 100, "a"), job(3, 200, "a"), job(4, 200, "a"), job(9, 50, "a")), 0);
        assertEquals(List.of(3, 4, 5, 9), ids(ranked));
    }

    @Test
    void orderDoesNotDependOnTheInputOrder() {
        List<JobProfile> jobs = List.of(job(1, 1, "a"), job(2, 2, "a"), job(3, 3, "b"), job(4, 4, "a", "b"));
        List<JobProfile> reversed = jobs.reversed();
        assertEquals(ids(ranker.rankJobs(seeker("s", "a"), jobs, 0)), ids(ranker.rankJobs(seeker("s", "a"), reversed, 0)));
    }

    @Test
    void limitKeepsTheBestAndZeroMeansAll() {
        List<JobProfile> jobs = List.of(job(1, 1, "b"), job(2, 1, "a"), job(3, 1, "b"));
        assertEquals(List.of(2), ids(ranker.rankJobs(seeker("s", "a"), jobs, 1)));
        assertEquals(3, ranker.rankJobs(seeker("s", "a"), jobs, 0).size());
        assertEquals(3, ranker.rankJobs(seeker("s", "a"), jobs, 10).size());
    }

    @Test
    void emptyInputGivesEmptyOutput() {
        assertEquals(List.of(), ranker.rankJobs(seeker("s"), List.of(), 0));
        assertEquals(List.of(), ranker.rankCandidates(job(1, 1, "a"), List.of(), 0));
    }

    @Test
    void candidatesAreOrderedByScoreThenSeekerId() {
        List<RankedCandidate> ranked = ranker.rankCandidates(job(1, 1, "a"),
                List.of(seeker("z", "a"), seeker("m"), seeker("b", "a")), 2);
        assertEquals(List.of("b", "z"), ranked.stream().map(RankedCandidate::getSeekerId).toList());
    }
}
