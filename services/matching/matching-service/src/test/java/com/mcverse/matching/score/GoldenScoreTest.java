package com.mcverse.matching.score;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.util.JsonFormat;
import com.mcverse.matching.v1.ScoreRequest;
import com.mcverse.matching.v1.ScoreResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The scorer must reproduce the hand-checked answers in services/matching/golden. */
class GoldenScoreTest {

    private static final Path GOLDEN = Path.of("..", "golden");

    @Test
    void scoreBasicMatchesTheGoldenResponse() throws IOException {
        ScoreRequest.Builder request = ScoreRequest.newBuilder();
        JsonFormat.parser().merge(Files.readString(GOLDEN.resolve("score-basic.request.json")), request);
        ScoreResponse.Builder expected = ScoreResponse.newBuilder();
        JsonFormat.parser().merge(Files.readString(GOLDEN.resolve("score-basic.response.json")), expected);

        var actual = new Scorer(ScoringWeights.defaults()).score(request.getSeeker(), request.getJob());
        var want = expected.getMatch();

        assertEquals(want.getScore(), actual.getScore());
        assertEquals(want.getMatchedSkillIdsList(), actual.getMatchedSkillIdsList());
        assertEquals(want.getMissingSkillIdsList(), actual.getMissingSkillIdsList());
        assertEquals(want.getPartsCount(), actual.getPartsCount());
        for (int i = 0; i < want.getPartsCount(); i++) {
            assertEquals(want.getParts(i).getKey(), actual.getParts(i).getKey());
            assertEquals(want.getParts(i).getCounted(), actual.getParts(i).getCounted());
            assertEquals(want.getParts(i).getWeight(), actual.getParts(i).getWeight(), 1e-9);
            assertEquals(want.getParts(i).getValue(), actual.getParts(i).getValue(), 1e-9);
        }
    }
}
