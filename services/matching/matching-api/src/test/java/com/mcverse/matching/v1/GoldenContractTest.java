package com.mcverse.matching.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The golden files in services/matching/golden are shared with Jobify's tests: both sides parse the
 * same JSON, so a contract change that breaks either side fails here first.
 */
class GoldenContractTest {

    private static final Path GOLDEN = Path.of("..", "golden");

    @Test
    void scoreRequestGoldenParsesAndRoundTrips() throws IOException {
        String json = Files.readString(GOLDEN.resolve("score-basic.request.json"));
        ScoreRequest.Builder builder = ScoreRequest.newBuilder();
        JsonFormat.parser().merge(json, builder);
        ScoreRequest request = builder.build();

        assertEquals(42, request.getJob().getJobId());
        assertEquals(3, request.getJob().getRequiredSkillsCount());
        assertEquals(Proficiency.PROFICIENCY_ADVANCED, request.getSeeker().getSkills(0).getProficiency());
        assertTrue(request.getSeeker().hasPreferences());
        assertTrue(request.getSeeker().getPreferences().hasMinAnnualRate());

        ScoreRequest again = ScoreRequest.parseFrom(request.toByteArray());
        assertEquals(request, again);
    }

    @Test
    void absentPreferencesAreDistinguishableFromEmptyOnes() {
        SeekerProfile none = SeekerProfile.newBuilder().setSeekerId("s").build();
        SeekerProfile empty = none.toBuilder().setPreferences(Preferences.getDefaultInstance()).build();

        assertTrue(!none.hasPreferences());
        assertTrue(empty.hasPreferences());
    }
}
